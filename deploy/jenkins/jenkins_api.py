# -*- coding: utf-8 -*-
"""Jenkins REST 访问的最小封装（crumb + Cookie 会话 + Basic Auth）。

凭据来源（按优先级）：
  1. 环境变量 JENKINS_URL / JENKINS_USER + JENKINS_TOKEN（推荐 API Token）
     或 JENKINS_PASS；
  2. 本地文件 deploy/jenkins/.jenkins.env（已 gitignore，格式 KEY=VALUE 每行一条）；
  3. 都没有 → 抛 CredentialsMissing，调用方据此提示去 docs/test-env.md §6.1 取测试凭据。

仓库内**不允许**出现任何 Jenkins 密码/Token（CLAUDE.md §6.7）。
"""
from __future__ import annotations

import http.cookiejar
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from base64 import b64encode
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
LOCAL_ENV = HERE / ".jenkins.env"


class CredentialsMissing(RuntimeError):
    """缺少 Jenkins 访问凭据。"""


class JenkinsError(RuntimeError):
    """Jenkins 返回非预期结果。"""


def _load_local_env() -> dict[str, str]:
    if not LOCAL_ENV.is_file():
        return {}
    out: dict[str, str] = {}
    for line in LOCAL_ENV.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        out[key.strip()] = value.strip().strip('"').strip("'")
    return out


def _settings() -> dict[str, str]:
    local = _load_local_env()
    url = os.environ.get("JENKINS_URL") or local.get("JENKINS_URL") or ""
    user = os.environ.get("JENKINS_USER") or local.get("JENKINS_USER") or ""
    secret = os.environ.get("JENKINS_TOKEN") or os.environ.get("JENKINS_PASS") or local.get("JENKINS_TOKEN") or local.get("JENKINS_PASS") or ""
    missing = [n for n, v in (("JENKINS_URL", url), ("JENKINS_USER", user), ("JENKINS_TOKEN/JENKINS_PASS", secret)) if not v]
    if missing:
        raise CredentialsMissing(
            "缺少 Jenkins 凭据：" + ", ".join(missing)
            + "\n  测试环境地址与账号见 docs/test-env.md §4 / §6.1。"
            + "\n  推荐写法（PowerShell，仅当前会话生效）："
            + "\n    $env:JENKINS_URL='http://192.168.99.100:8080'"
            + "\n    $env:JENKINS_USER='qjj'"
            + "\n    $env:JENKINS_TOKEN='<API Token>'"
            + f"\n  或写入 {LOCAL_ENV}（已 gitignore）"
        )
    return {"url": url.rstrip("/"), "user": user, "secret": secret}


class Jenkins:
    def __init__(self, timeout: int = 30) -> None:
        cfg = _settings()
        self.url = cfg["url"]
        self._auth = b64encode(f"{cfg['user']}:{cfg['secret']}".encode("utf-8")).decode("ascii")
        self.timeout = timeout
        self._jar = http.cookiejar.CookieJar()
        self._opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self._jar))

    # ---- 基础请求 -------------------------------------------------------
    def _crumb(self) -> dict[str, str]:
        req = urllib.request.Request(f"{self.url}/crumbIssuer/api/json", headers={"Authorization": f"Basic {self._auth}"})
        with self._opener.open(req, timeout=self.timeout) as resp:
            return json.loads(resp.read().decode("utf-8", errors="replace"))

    def request(self, method: str, path: str, body: bytes | None = None,
                content_type: str = "application/xml") -> bytes:
        headers = {"Authorization": f"Basic {self._auth}"}
        if method != "GET":
            # crumb 与 POST 必须共享同一个 JSESSIONID，故用同一 opener
            crumb = self._crumb()
            headers[crumb["crumbRequestField"]] = crumb["crumb"]
        if body is not None:
            headers["Content-Type"] = content_type
        req = urllib.request.Request(f"{self.url}{path}", data=body, headers=headers, method=method)
        try:
            with self._opener.open(req, timeout=self.timeout) as resp:
                return resp.read()
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", errors="replace")[:2000]
            raise JenkinsError(f"{method} {path} -> HTTP {e.code}: {detail}") from e
        except urllib.error.URLError as e:
            raise JenkinsError(f"{method} {path} -> 连接失败: {e.reason}") from e

    # ---- 语义封装 -------------------------------------------------------
    def ping(self) -> dict[str, Any]:
        return json.loads(self.request("GET", "/api/json?tree=mode,nodeDescription,useSecurity").decode("utf-8"))

    def job_exists(self, name: str) -> bool:
        try:
            self.request("GET", f"/job/{urllib.parse.quote(name)}/api/json")
            return True
        except JenkinsError as e:
            if "HTTP 404" in str(e):
                return False
            raise

    def get_config(self, name: str) -> bytes:
        return self.request("GET", f"/job/{urllib.parse.quote(name)}/config.xml")

    def create_job(self, name: str, config: bytes) -> None:
        q = urllib.parse.urlencode({"name": name})
        self.request("POST", f"/createItem?{q}", body=config)

    def update_job(self, name: str, config: bytes) -> None:
        self.request("POST", f"/job/{urllib.parse.quote(name)}/config.xml", body=config)

    def credential_ids(self) -> list[str]:
        """列出系统级凭据 ID（需要 Overall/Read 权限；失败由调用方降级处理）。"""
        raw = self.request(
            "GET",
            "/credentials/store/system/domain/_/api/json?tree=credentials[id,typeName]",
        )
        data = json.loads(raw.decode("utf-8", errors="replace"))
        return [c.get("id", "") for c in data.get("credentials", [])]
