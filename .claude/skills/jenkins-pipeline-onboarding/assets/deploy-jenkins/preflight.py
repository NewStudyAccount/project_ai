# -*- coding: utf-8 -*-
"""为 jobs.yaml 里的每条流水线做「开工前预检」——只读，不改任何东西。

设计意图：把 docs/jenkins-pipeline-guide.md 里「禁止编造主机/端口/凭据」
从一句告示变成可执行门禁。任何 ERROR 都应当阻止创建 Job。

用法：
    python deploy/jenkins/preflight.py            # 离线检查（默认）
    python deploy/jenkins/preflight.py --json     # 机器可读
    python deploy/jenkins/preflight.py --online   # 额外检查 Jenkins 可达性与凭据 ID

退出码：0 = 无 ERROR；1 = 有 ERROR；2 = 清单本身读不了。
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import render

REPO = render.REPO_ROOT
PORT_REGISTRY = REPO / "docs" / "port-registry.md"

SECRET_PATTERNS = [
    re.compile(r"(?i)\b(password|passwd|secret|token)\s*[:=]\s*['\"]?[^\s'\"#]{4,}"),
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"(?i)\bbasic\s+[A-Za-z0-9+/]{16,}={0,2}"),
]


class Finding:
    __slots__ = ("level", "scope", "message")

    def __init__(self, level: str, scope: str, message: str) -> None:
        self.level, self.scope, self.message = level, scope, message

    def as_dict(self) -> dict[str, str]:
        return {"level": self.level, "scope": self.scope, "message": self.message}


def _read_text_lenient(path: Path) -> str:
    """port-registry.md 等历史文档可能不是 UTF-8；数字与英文关键字仍可安全匹配。"""
    return path.read_bytes().decode("utf-8", errors="replace")


def check_units(spec: dict, findings: list[Finding]) -> None:
    for job in spec["jobs"]:
        resolved = render.resolve(job, spec["defaults"])
        name = resolved["name"]
        unit = REPO / resolved["unit_dir"]

        if not unit.is_dir():
            findings.append(Finding("ERROR", name, f"可部署单元目录不存在：{resolved['unit_dir']}"))
            continue

        if not (unit / "Dockerfile").is_file():
            findings.append(Finding(
                "ERROR", name,
                f"{resolved['unit_dir']} 下没有 Dockerfile，Image 阶段的 docker build 必然失败"
                + (f"（提示：该系统的启动模块可能不是 {resolved['module']}）" if resolved["module"] else ""),
            ))

        if resolved["kind"] == "frontend":
            pkg = unit / "package.json"
            if not pkg.is_file():
                findings.append(Finding("ERROR", name, f"缺少 package.json：{resolved['unit_dir']}"))
            else:
                # utf-8-sig：容忍 Windows 编辑器写入的 BOM；解析失败要报 ERROR 而不是抛异常
                try:
                    data = json.loads(pkg.read_text(encoding="utf-8-sig"))
                except (json.JSONDecodeError, UnicodeDecodeError) as e:
                    findings.append(Finding("ERROR", name, f"package.json 不是合法 JSON：{e}"))
                    data = {}
                scripts = data.get("scripts") or {}
                need = ["lint", "type-check", "build"]
                lost = [s for s in need if s not in scripts]
                if lost:
                    findings.append(Finding("ERROR", name, f"package.json 缺少脚本：{', '.join(lost)}（质量门会失败）"))
                if "test" not in scripts:
                    findings.append(Finding("INFO", name, "package.json 无 test 脚本，质量门不跑单测（与现状一致）"))
        else:
            if not (unit / "pom.xml").is_file():
                findings.append(Finding("ERROR", name, f"缺少 pom.xml：{resolved['unit_dir']}"))


def check_ports(spec: dict, findings: list[Finding]) -> None:
    registry_text = _read_text_lenient(PORT_REGISTRY) if PORT_REGISTRY.is_file() else ""
    if not PORT_REGISTRY.is_file():
        findings.append(Finding("WARN", "global", "docs/port-registry.md 不存在，无法校验端口登记"))

    seen: dict[str, str] = {}
    images: dict[str, str] = {}
    for job in spec["jobs"]:
        resolved = render.resolve(job, spec["defaults"])
        name, port = resolved["name"], resolved["port"]

        if port in seen:
            findings.append(Finding("ERROR", name, f"端口 {port} 与 {seen[port]} 冲突"))
        seen[port] = name

        if registry_text and not re.search(rf"(?<!\d){re.escape(port)}(?!\d)", registry_text):
            findings.append(Finding("ERROR", name, f"端口 {port} 未在 docs/port-registry.md 登记"))

        if resolved["image"] in images:
            findings.append(Finding("ERROR", name, f"镜像名 {resolved['image']} 与 {images[resolved['image']]} 冲突"))
        images[resolved["image"]] = name

        expect = "/healthz" if resolved["kind"] == "frontend" else "/actuator/health"
        if resolved["health_path"] != expect:
            findings.append(Finding("WARN", name, f"健康检查路径为 {resolved['health_path']}，本项目约定 {expect}"))


def check_secrets(spec_path: Path, findings: list[Finding]) -> None:
    targets = [spec_path, *sorted((render.TEMPLATES).glob("*.tmpl"))]
    for path in targets:
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for pattern in SECRET_PATTERNS:
            for m in pattern.finditer(text):
                line = text.count("\n", 0, m.start()) + 1
                findings.append(Finding("ERROR", path.name, f"疑似明文敏感信息（第 {line} 行）：{m.group(0)[:60]}"))


def _read_pom(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def check_deployability(spec: dict, findings: list[Finding]) -> None:
    """从仓库事实推导「可部署性」，而不是让人在文档里手写「当前覆盖」清单。

    三个推导（都在仓库里有唯一物证，不需要额外登记）：
      1. 有 Dockerfile 但 jobs.yaml 没登记 → 覆盖缺口；
      2. 有 Dockerfile 但 pom 没声明 spring-boot-maven-plugin → 产出瘦 jar，
         镜像里 java -jar 会因无 Main-Class 启动失败；
      3. 前端有 package.json 却既无 Dockerfile 又未登记 → 想建也发不出去。
    """
    registered = {render.resolve(j, spec["defaults"])["unit_dir"] for j in spec["jobs"]}

    for dockerfile in sorted((REPO / "backend").rglob("Dockerfile")):
        unit = dockerfile.parent
        if any(part in ("target", "node_modules") for part in unit.parts):
            continue
        rel = unit.relative_to(REPO).as_posix()
        if rel not in registered:
            findings.append(Finding("WARN", rel, "目录内有 Dockerfile 但 jobs.yaml 未登记流水线"))
        pom = unit / "pom.xml"
        if pom.is_file() and "spring-boot-maven-plugin" not in _read_pom(pom):
            findings.append(Finding(
                "WARN", rel,
                "pom.xml 未声明 spring-boot-maven-plugin，mvn package 产出瘦 jar（无 Main-Class），"
                "容器 java -jar 会启动失败",
            ))

    for pkg in sorted((REPO / "frontend").glob("*/package.json")):
        unit = pkg.parent
        if any(part in ("node_modules", "dist") for part in unit.parts):
            continue
        rel = unit.relative_to(REPO).as_posix()
        if rel in registered or (unit / "Dockerfile").is_file():
            continue
        findings.append(Finding("WARN", rel, "前端目录缺 Dockerfile，无法构建发布镜像；补齐后才能建 CD"))


def check_online(spec: dict, findings: list[Finding]) -> None:
    import jenkins_api

    try:
        api = jenkins_api.Jenkins()
    except jenkins_api.CredentialsMissing as e:
        findings.append(Finding("WARN", "online", f"跳过在线检查：{e}".replace("\n", " ")))
        return

    try:
        api.ping()
    except Exception as e:  # noqa: BLE001 - 网络原因多样，统一降级为提示
        findings.append(Finding("WARN", "online", f"Jenkins 不可达，跳过在线检查：{e}"))
        return

    try:
        ids = set(api.credential_ids())
    except Exception as e:  # noqa: BLE001 - 权限不足时不该判定为失败
        findings.append(Finding("WARN", "online", f"无法列出 Jenkins 凭据（权限不足？）：{e}"))
        ids = set()

    if ids:
        for job in spec["jobs"]:
            resolved = render.resolve(job, spec["defaults"])
            for key in ("git_cred_id", "ssh_cred_id", "registry_cred_id"):
                cid = resolved[key]
                if cid and cid not in ids:
                    findings.append(Finding("ERROR", resolved["name"], f"凭据 ID 不存在于 Jenkins：{key}={cid}"))

    for job in spec["jobs"]:
        name = job["name"]
        findings.append(Finding(
            "INFO", name,
            "Job 已存在，将覆盖 config.xml" if api.job_exists(name) else "Job 不存在，将新建",
        ))


def main(argv: list[str]) -> int:
    render.ensure_utf8_stdout()
    as_json = "--json" in argv
    online = "--online" in argv
    spec_path = render.DEFAULT_SPEC

    findings: list[Finding] = []
    try:
        spec = render.load_spec(spec_path)
    except render.SpecError as e:
        print(f"[FATAL] {e}", file=sys.stderr)
        return 2

    check_units(spec, findings)
    check_ports(spec, findings)
    check_secrets(spec_path, findings)
    check_deployability(spec, findings)
    if online:
        check_online(spec, findings)

    errors = [f for f in findings if f.level == "ERROR"]
    warns = [f for f in findings if f.level == "WARN"]

    if as_json:
        print(json.dumps({
            "spec": str(spec_path),
            "jobs": len(spec["jobs"]),
            "errors": len(errors),
            "warnings": len(warns),
            "findings": [f.as_dict() for f in findings],
            "passed": not errors,
        }, ensure_ascii=False, indent=2))
        return 0 if not errors else 1

    print(f"== preflight: {spec_path.name} ({len(spec['jobs'])} 条流水线) ==")
    for job in spec["jobs"]:
        resolved = render.resolve(job, spec["defaults"])
        own = [f for f in findings if f.scope == resolved["name"]]
        bad = [f for f in own if f.level == "ERROR"]
        mark = "FAIL" if bad else "OK  "
        port = resolved["port"]
        print(f"[{mark}] {resolved['name']:<18} {resolved['unit_dir']:<32} port {port}")
        for f in own:
            print(f"        - {f.level}: {f.message}")

    others = [f for f in findings if f.scope not in {j["name"] for j in spec["jobs"]}]
    if others:
        print("\n-- 全局 --")
        for f in others:
            print(f"[{f.level}] {f.scope}: {f.message}")

    print(f"\n汇总: {len(spec['jobs'])} 条流水线 | ERROR {len(errors)} | WARN {len(warns)} | "
          + ("PASS" if not errors else "FAIL（存在 ERROR，禁止创建/更新 Job）"))
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

