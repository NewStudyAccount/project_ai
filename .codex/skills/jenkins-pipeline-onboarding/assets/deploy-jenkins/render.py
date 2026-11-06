# -*- coding: utf-8 -*-
"""把 deploy/jenkins/jobs.yaml 渲染成 Jenkins Job 的 config.xml。

只做纯渲染，不联网、不写 Jenkins、不含任何凭据：
  - 模板在 deploy/jenkins/templates/，占位符形如 {{KEY}}，与 Groovy 的 $ 语法不冲突；
  - 事实源是 jobs.yaml，本文件不保存任何 Job 的业务参数。

被 create-jobs.py / preflight.py 复用。
"""
from __future__ import annotations

import posixpath
import re
from pathlib import Path
from typing import Any

import yaml

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent.parent
TEMPLATES = HERE / "templates"
DEFAULT_SPEC = HERE / "jobs.yaml"

PLACEHOLDER = re.compile(r"\{\{([A-Z0-9_]+)\}\}")


class SpecError(RuntimeError):
    """jobs.yaml 内容不合法（结构/字段/取值）。"""


def load_spec(path: Path | str = DEFAULT_SPEC) -> dict[str, Any]:
    p = Path(path)
    if not p.is_file():
        raise SpecError(f"找不到 Job 清单：{p}")
    with p.open(encoding="utf-8-sig") as f:
        raw = yaml.safe_load(f)
    if not isinstance(raw, dict):
        raise SpecError(f"{p} 顶层必须是映射（mapping）")

    defaults = raw.get("defaults") or {}
    if not isinstance(defaults, dict):
        raise SpecError("defaults 必须是映射")
    jobs = raw.get("jobs") or []
    if not isinstance(jobs, list) or not jobs:
        raise SpecError("jobs 必须是非空列表")

    required = ("name", "kind", "system", "target", "port", "health_path")
    seen: set[str] = set()
    for i, job in enumerate(jobs):
        if not isinstance(job, dict):
            raise SpecError(f"jobs[{i}] 必须是映射")
        missing = [k for k in required if not job.get(k)]
        if not job.get("module") and job.get("kind") == "backend":
            missing = [m for m in missing if m != "module"]
        if missing:
            raise SpecError(f"jobs[{i}]（{job.get('name', '?')}）缺少必填字段：{', '.join(missing)}")
        if job["kind"] not in ("frontend", "backend"):
            raise SpecError(f"{job['name']}：kind 只能是 frontend / backend，实际 {job['kind']!r}")
        if job["name"] in seen:
            raise SpecError(f"Job 名重复：{job['name']}")
        seen.add(job["name"])
    return {"version": raw.get("version"), "updated": raw.get("updated"), "defaults": defaults, "jobs": jobs}


def resolve(job: dict[str, Any], defaults: dict[str, Any]) -> dict[str, str]:
    """把一条 Job 展开成渲染用的扁平字段（含派生值）。"""
    kind = job["kind"]
    target = str(job["target"])
    system = str(job["system"])
    port = str(job["port"])
    health_path = str(job["health_path"])
    registry = str(defaults.get("registry", ""))
    prefix = str(defaults.get("image_prefix", "project_ai"))

    if kind == "frontend":
        module = ""
        default_dir = Path("frontend") / system
        # 前端容器内固定监听 80（nginx），可省略
        container_port = str(job.get("container_port") or 80)
    else:
        module = str(job.get("module") or target)
        default_dir = Path("backend") / system / module
        # 后端必须显式声明 container_port（= application.yml 的 server.port），
        # 宿主机端口（port）由此解耦，两者不必相等
        if job.get("container_port") in (None, ""):
            raise SpecError(f"{job.get('name', '?')}：backend 必须声明 container_port（= application.yml 的 server.port）")
        container_port = str(job["container_port"])

    # 目录布局可覆盖：默认按 <frontend|backend>/<system>[/<module>] 推导，
    # 别的项目布局不同时在 jobs.yaml 里显式写 unit_path（相对仓库根）。
    unit_dir = Path(str(job["unit_path"])) if job.get("unit_path") else default_dir

    return {
        "name": str(job["name"]),
        "kind": kind,
        "system": system,
        "target": target,
        "module": module,
        "unit_dir": unit_dir.as_posix(),
        # port = 宿主机发布端口（唯一冲突面，port-registry.md 登记）；
        # container_port = 容器内监听端口（= 代码 server.port / nginx listen，容器间可重复）
        "port": port,
        "container_port": container_port,
        "health_path": health_path,
        "health_url": f"http://127.0.0.1:{port}{health_path}",
        "image": f"{registry}/{prefix}/{target}",
        "container": target,
        "description": str(job.get("description") or f"{target} {'frontend docker static' if kind == 'frontend' else 'docker jar'}"),
        "notes": str(job.get("notes") or ""),
        "owner": str(job.get("owner") or defaults.get("owner", "")),
        "branch": str(defaults.get("branch", "feature-base-design")),
        "git_url": str(defaults.get("git_url", "")),
        "git_cred_id": str(defaults.get("git_cred_id", "")),
        "deploy_host": str(defaults.get("deploy_host", "")),
        "deploy_user": str(defaults.get("deploy_user", "")),
        "ssh_cred_id": str(defaults.get("ssh_cred_id", "")),
        "registry": registry,
        "registry_cred_id": str(defaults.get("registry_cred_id", "")),
        "network": str(defaults.get("docker_network", "project-ai")),
        "node_image": str(defaults.get("node_image", "node:24-alpine")),
        "maven_image": str(defaults.get("maven_image", "maven:3.9-eclipse-temurin-21")),
        # 后端容器运行期依赖地址：同 docker 网络内按容器名解析
        "mysql_host": str(defaults.get("mysql_host", "crm-mysql")),
        "redis_host": str(defaults.get("redis_host", "crm-redis")),
        "nacos_addr": str(defaults.get("nacos_addr", "crm-nacos:8848")),
        "agent": agent_directive(str(defaults.get("agent_label", "any"))),
    }


def agent_directive(label: str) -> str:
    """把 agent_label 翻成 declarative 的 agent 段：any → `any`，其余 → `label 'xxx'`。"""
    label = label.strip()
    if not label or label.lower() == "any":
        return "any"
    return f"label '{label}'"


def ensure_utf8_stdout() -> None:
    """被管道/AI 捕获时强制 UTF-8，避免 Windows 默认代码页把中文输出成乱码。

    交互式终端（isatty）保持系统默认，交给终端自己渲染。
    """
    import sys

    for stream in (sys.stdout, sys.stderr):
        if stream is not None and not stream.isatty():
            try:
                stream.reconfigure(encoding="utf-8")
            except (AttributeError, ValueError):
                pass


def _render_template(name: str, values: dict[str, str]) -> str:
    text = (TEMPLATES / name).read_text(encoding="utf-8-sig")
    missing: list[str] = []

    def sub(m: re.Match[str]) -> str:
        key = m.group(1)
        if key not in values:
            missing.append(key)
            return m.group(0)
        return values[key]

    out = PLACEHOLDER.sub(sub, text)
    if missing:
        raise SpecError(f"模板 {name} 需要未提供的占位符：{', '.join(sorted(set(missing)))}")
    return out


def render_script(job: dict[str, str]) -> str:
    """生成 Job 内嵌的 Jenkins Pipeline 脚本（Groovy）。"""
    values = {
        "BRANCH": job["branch"],
        "GIT_URL": job["git_url"],
        "GIT_CRED_ID": job["git_cred_id"],
        "DEPLOY_HOST": job["deploy_host"],
        "DEPLOY_USER": job["deploy_user"],
        "SSH_CRED": job["ssh_cred_id"],
        "REGISTRY": job["registry"],
        "REGISTRY_CRED": job["registry_cred_id"],
        "IMAGE": job["image"],
        "CONTAINER": job["container"],
        "PORT": job["port"],
        "CONTAINER_PORT": job["container_port"],
        "HEALTH_URL": job["health_url"],
        "NETWORK": job["network"],
        "NODE_IMAGE": job["node_image"],
        "MAVEN_IMAGE": job["maven_image"],
        "APP_DIR": job["unit_dir"],
        # 后端 SYS_DIR = 可部署单元的父目录（mvn -pl 的根），随 unit_path 自动适配
        "SYS_DIR": posixpath.dirname(job["unit_dir"]),
        "MODULE": posixpath.basename(job["unit_dir"]) if job["kind"] == "backend" else "",
        "MYSQL_HOST": job["mysql_host"],
        "REDIS_HOST": job["redis_host"],
        "NACOS_ADDR": job["nacos_addr"],
        "AGENT": job["agent"],
    }
    tpl = "frontend.groovy.tmpl" if job["kind"] == "frontend" else "backend.groovy.tmpl"
    return _render_template(tpl, values)


def render_xml(job: dict[str, str], script: str) -> bytes:
    """生成 Jenkins Job 的 config.xml（Pipeline 内嵌，Jenkinsfile 不入仓）。"""
    xml = _render_template(
        "job.xml.tmpl",
        {"DESCRIPTION": job["description"], "BRANCH": job["branch"], "SCRIPT": script},
    )
    return xml.encode("utf-8")


def render_all(spec: dict[str, Any]) -> list[tuple[dict[str, str], bytes, str]]:
    """返回 [(resolved_job, config_xml_bytes, script), ...]，顺序与 jobs.yaml 一致。"""
    out = []
    for job in spec["jobs"]:
        resolved = resolve(job, spec["defaults"])
        script = render_script(resolved)
        out.append((resolved, render_xml(resolved, script), script))
    return out
