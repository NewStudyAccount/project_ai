# -*- coding: utf-8 -*-
"""在 Jenkins 创建/更新流水线 Job（Pipeline 内嵌，Jenkinsfile 不入仓）。

事实源：deploy/jenkins/jobs.yaml
模板：  deploy/jenkins/templates/*.tmpl
凭据：  只从环境变量或 deploy/jenkins/.jenkins.env 读取（仓库内无密码）

用法：
    python deploy/jenkins/create-jobs.py --dry-run        # 离线渲染，不连 Jenkins
    python deploy/jenkins/create-jobs.py --check          # 只跑预检
    python deploy/jenkins/create-jobs.py                  # 预检 → 创建/更新 → 回读校验
    python deploy/jenkins/create-jobs.py --only user-service-cd
    python deploy/jenkins/create-jobs.py --skip-preflight # 已知风险时强制

退出码：0 成功；1 预检/应用/校验失败；2 清单不可读。
"""
from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
import tempfile
from pathlib import Path

import preflight
import render

SECRET_RE = re.compile(r"(?i)(password|passwd|secret|token)\s*[:=]\s*['\"]?[^\s'\"]{4,}")


def run_preflight(spec: dict) -> int:
    findings: list[preflight.Finding] = []
    preflight.check_units(spec, findings)
    preflight.check_ports(spec, findings)
    preflight.check_secrets(render.DEFAULT_SPEC, findings)
    preflight.check_deployability(spec, findings)
    errors = [f for f in findings if f.level == "ERROR"]
    for f in findings:
        if f.level in ("ERROR", "WARN"):
            print(f"  [{f.level}] {f.scope}: {f.message}")
    if errors:
        print(f"预检未通过：{len(errors)} 个 ERROR，已中止（修完再跑；确需强行可用 --skip-preflight）")
        return 1
    print(f"预检通过：{len(spec['jobs'])} 条流水线")
    return 0


def verify_config(name: str, config: bytes, resolved: dict) -> list[str]:
    text = config.decode("utf-8", errors="replace")
    problems: list[str] = []
    if "<name>BRANCH</name>" not in text:
        problems.append("缺少 BRANCH 参数")
    if "DEPLOY_ENV" in text:
        problems.append("出现 DEPLOY_ENV（本项目不提供该参数）")
    if "spring.profiles" in text:
        problems.append("流水线注入了 spring.profiles（应由 application.yml 决定）")
    if SECRET_RE.search(text):
        problems.append("疑似明文密钥")
    if "docker run" not in text:
        problems.append("发布阶段没有 docker run")
    image = resolved["node_image"] if resolved["kind"] == "frontend" else resolved["maven_image"]
    if image not in text:
        problems.append(f"缺少构建镜像 {image}")
    if resolved["kind"] == "backend" and "mvn -pl" not in text:
        problems.append("后端缺少 mvn -pl 打包")
    if resolved["kind"] == "frontend" and "npm run lint" not in text:
        problems.append("前端缺少 lint 质量门")
    return problems


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description="按 jobs.yaml 创建/更新 Jenkins 流水线")
    ap.add_argument("--spec", default=str(render.DEFAULT_SPEC), help="Job 清单路径")
    ap.add_argument("--only", action="append", default=[], help="只处理指定 Job（可重复）")
    ap.add_argument("--dry-run", action="store_true", help="只渲染，不连 Jenkins")
    ap.add_argument("--check", action="store_true", help="只跑预检")
    ap.add_argument("--skip-preflight", action="store_true", help="跳过预检（不推荐）")
    ap.add_argument("--out", default="", help="dry-run 产物目录（默认临时目录）")
    args = ap.parse_args(argv)
    render.ensure_utf8_stdout()

    try:
        spec = render.load_spec(args.spec)
    except render.SpecError as e:
        print(f"[FATAL] {e}", file=sys.stderr)
        return 2

    # 预检必须基于完整清单：--only 只影响「应用哪些」，不能影响「检查哪些」，
    # 否则被过滤掉的条目会误报「有 Dockerfile 但未登记」。
    full_spec = spec
    if args.only:
        wanted = set(args.only)
        unknown = wanted - {j["name"] for j in spec["jobs"]}
        if unknown:
            print(f"[FATAL] jobs.yaml 中不存在：{', '.join(sorted(unknown))}", file=sys.stderr)
            return 2
        spec = {**spec, "jobs": [j for j in spec["jobs"] if j["name"] in wanted]}

    if not args.skip_preflight:
        rc = run_preflight(full_spec)
        if rc:
            return rc
    if args.check:
        return 0

    rendered = render.render_all(spec)

    if args.dry_run:
        out = Path(args.out) if args.out else Path(tempfile.gettempdir()) / "jenkins-render" / dt.datetime.now().strftime("%Y%m%d-%H%M%S")
        out.mkdir(parents=True, exist_ok=True)
        failed = 0
        for job, config, script in rendered:
            (out / f"{job['name']}.groovy").write_text(script, encoding="utf-8")
            (out / f"{job['name']}.config.xml").write_bytes(config)
            problems = verify_config(job["name"], config, job)
            mark = "OK  " if not problems else "FAIL"
            print(f"[{mark}] {job['name']:<18} {job['unit_dir']:<32} port {job['port']}"
                  + ("" if not problems else " -> " + "; ".join(problems)))
            failed += bool(problems)
        print(f"\n产物目录：{out}")
        print(f"dry-run：{len(rendered) - failed}/{len(rendered)} 通过静态校验")
        return 1 if failed else 0

    import jenkins_api

    try:
        api = jenkins_api.Jenkins()
    except jenkins_api.CredentialsMissing as e:
        print(f"[FATAL] {e}", file=sys.stderr)
        return 1

    backup = Path(tempfile.gettempdir()) / "jenkins-job-backup" / dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup.mkdir(parents=True, exist_ok=True)
    done: list[tuple[str, str, Path | None]] = []
    failed = 0

    for job, config, _script in rendered:
        name = job["name"]
        old_path: Path | None = None
        try:
            if api.job_exists(name):
                old = api.get_config(name)
                old_path = backup / f"{name}.config.xml"
                old_path.write_bytes(old)
                api.update_job(name, config)
                action = "updated"
            else:
                api.create_job(name, config)
                action = "created"
            fresh = api.get_config(name)
            problems = verify_config(name, fresh, job)
            if problems:
                raise RuntimeError("回读校验失败：" + "; ".join(problems))
            print(f"[OK  ] {name:<18} {action}")
            done.append((name, action, old_path))
        except Exception as e:  # noqa: BLE001 - 任何单条失败都要报告并保留回滚线索
            failed += 1
            print(f"[FAIL] {name:<18} {e}")

    if failed:
        print(f"\n失败 {failed} 条。已处理清单（供回滚）：")
        for name, action, old_path in done:
            target = f"恢复到备份：{old_path}" if old_path else "新建的 Job，回滚 = 删除该 Job"
            print(f"  - {name} ({action}) -> {target}")
        print(f"备份目录：{backup}")

    listing = api.request("GET", "/api/json?tree=jobs[name]").decode("utf-8", errors="replace")
    print("\n==== Jenkins 现有 Job ====")
    print(listing)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
