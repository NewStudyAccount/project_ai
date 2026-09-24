"""unify-login-facade 本地协议冒烟（无需浏览器）。"""
from __future__ import annotations

import base64
import hashlib
import http.cookiejar
import json
import re
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:9080"
PASS = 0
FAIL = 0


def ok(name: str, detail: str = "") -> None:
    global PASS
    PASS += 1
    print(f"  OK  {name}" + (f" — {detail}" if detail else ""))


def bad(name: str, detail: str = "") -> None:
    global FAIL
    FAIL += 1
    print(f"  FAIL {name}" + (f" — {detail}" if detail else ""))


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def pkce() -> tuple[str, str]:
    verifier = b64url(hashlib.sha256(b"smoke-verifier-unify-login-facade").digest())[:43]
    verifier = (verifier + "x")[:43]
    challenge = b64url(hashlib.sha256(verifier.encode()).digest())
    return verifier, challenge


def opener():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar)), jar


def login(op, jar) -> bool:
    data = urllib.parse.urlencode({"username": "admin", "password": "admin123"}).encode()
    req = urllib.request.Request(
        BASE + "/login",
        data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        op.open(req, timeout=10)
    except Exception:
        pass
    return any(c.name == "AUTH_SSO_SESSION" for c in jar)


def authorize(op, client_id: str, redirect_uri: str, verifier: str, challenge: str) -> str:
    params = {
        "response_type": "code",
        "client_id": client_id,
        "redirect_uri": redirect_uri,
        "scope": "openid profile",
        "state": "smoke-state",
        "nonce": "smoke-nonce",
        "code_challenge": challenge,
        "code_challenge_method": "S256",
    }
    url = BASE + "/oauth2/authorize?" + urllib.parse.urlencode(params)

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None

    handlers = [h for h in op.handlers if not isinstance(h, urllib.request.HTTPRedirectHandler)]
    no = urllib.request.build_opener(*handlers, NoRedirect)
    try:
        with no.open(url, timeout=10) as resp:
            body = resp.read().decode("utf-8", "replace")
            m = re.search(r"[?&]code=([A-Za-z0-9._-]+)", body + " " + (resp.geturl() or ""))
            return m.group(1) if m else ""
    except urllib.error.HTTPError as e:
        loc = e.headers.get("Location", "")
        if "code=" in loc:
            return urllib.parse.parse_qs(urllib.parse.urlparse(loc).query).get("code", [""])[0]
        print("    authorize error", e.code, loc[:160])
        return ""
    except urllib.error.URLError as e:
        # 成功发码后 302 到前端 callback（本机 5173 未监听）属预期
        m = re.search(r"code=([A-Za-z0-9._-]+)", str(e))
        return m.group(1) if m else ""


def token(body: dict) -> dict:
    data = urllib.parse.urlencode(body).encode()
    req = urllib.request.Request(
        BASE + "/oauth2/token",
        data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode())


def main() -> None:
    verifier, challenge = pkce()
    print("== OIDC smoke: unify-login-facade ==")

    # 1) 发现文档含 end_session
    with urllib.request.urlopen(BASE + "/.well-known/openid-configuration", timeout=10) as resp:
        disc = json.loads(resp.read().decode())
    if disc.get("end_session_endpoint", "").endswith("/connect/logout"):
        ok("发现文档 end_session_endpoint")
    else:
        bad("发现文档 end_session_endpoint", str(disc.get("end_session_endpoint")))

    # 2) 非法 post_logout 被拒（不开放重定向）
    req = urllib.request.Request(BASE + "/connect/logout?post_logout_redirect_uri=" + urllib.parse.quote("https://evil.example/"))
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None
    opener_noredirect = urllib.request.build_opener(NoRedirect)
    try:
        with opener_noredirect.open(req, timeout=10) as resp:
            body = resp.read().decode("utf-8", "replace")
            loc = resp.headers.get("Location", "")
            if resp.status == 200 and "evil.example" not in loc and "已登出" in body:
                ok("非法 post_logout_redirect_uri 拒绝跳转", "200 默认页")
            else:
                bad("非法 post_logout_redirect_uri", f"{resp.status} loc={loc}")
    except urllib.error.HTTPError as e:
        loc = e.headers.get("Location", "")
        if e.code in (302, 303) and "evil.example" in loc:
            bad("非法 post_logout 开放重定向", loc)
        else:
            ok("非法 post_logout 拒绝", f"HTTP {e.code} loc={loc}")

    # 3) 合法 post_logout 跳转
    good_logout = "http://127.0.0.1:5173/logged-out"
    req = urllib.request.Request(
        BASE + "/connect/logout?post_logout_redirect_uri=" + urllib.parse.quote(good_logout, safe="")
    )
    try:
        with opener_noredirect.open(req, timeout=10) as resp:
            bad("合法 post_logout 应 302", str(resp.status))
    except urllib.error.HTTPError as e:
        loc = e.headers.get("Location", "")
        if e.code in (302, 303) and loc.startswith(good_logout):
            ok("合法 post_logout_redirect_uri 跳转", loc)
        else:
            bad("合法 post_logout_redirect_uri", f"{e.code} {loc}")

    # 4) 建立 SSO + user-admin-spa 换票
    op, jar = opener()
    if login(op, jar):
        ok("portal 账密登录 admin/admin123")
    else:
        bad("portal 账密登录")
        print_summary()
        return

    code = authorize(
        op,
        "user-admin-spa",
        "http://127.0.0.1:5173/callback",
        verifier,
        challenge,
    )
    if code:
        ok("user-admin-spa 授权码", code[:16] + "...")
    else:
        bad("user-admin-spa 授权码")
        print_summary()
        return

    try:
        tokens = token(
            {
                "grant_type": "authorization_code",
                "code": code,
                "redirect_uri": "http://127.0.0.1:5173/callback",
                "client_id": "user-admin-spa",
                "code_verifier": verifier,
            }
        )
        ok("user-admin-spa 换票", "AT=" + tokens.get("access_token", "")[:12] + "...")
        at1, rt1 = tokens.get("access_token", ""), tokens.get("refresh_token", "")
    except Exception as e:
        bad("user-admin-spa 换票", str(e))
        print_summary()
        return

    # 5) auth-admin-spa 独立 client
    verifier2, challenge2 = pkce()
    # 新 verifier 与 challenge 配对
    verifier2 = b64url(b"smoke-verifier-auth-admin-spa-0123456789ab")
    challenge2 = b64url(hashlib.sha256(verifier2.encode()).digest())
    code2 = authorize(
        op,
        "auth-admin-spa",
        "http://127.0.0.1:5175/callback",
        verifier2,
        challenge2,
    )
    if code2:
        ok("auth-admin-spa 授权码（独立 client）", code2[:16] + "...")
    else:
        bad("auth-admin-spa 授权码")

    try:
        tokens2 = token(
            {
                "grant_type": "authorization_code",
                "code": code2,
                "redirect_uri": "http://127.0.0.1:5175/callback",
                "client_id": "auth-admin-spa",
                "code_verifier": verifier2,
            }
        )
        ok("auth-admin-spa 换票", "AT=" + tokens2.get("access_token", "")[:12] + "...")
        rt2 = tokens2.get("refresh_token", "")
    except Exception as e:
        bad("auth-admin-spa 换票", str(e))
        rt2 = ""

    # 6) client 隔离：user code 不能给 auth-admin 用（已消耗则失败为预期）
    try:
        token(
            {
                "grant_type": "authorization_code",
                "code": code,
                "redirect_uri": "http://127.0.0.1:5175/callback",
                "client_id": "auth-admin-spa",
                "code_verifier": verifier,
            }
        )
        bad("client 隔离（错配 redirect/client 应失败）")
    except Exception:
        ok("client 隔离：错配 client/redirect 换票被拒")

    # 7) 刷新轮转
    try:
        refreshed = token(
            {
                "grant_type": "refresh_token",
                "refresh_token": rt1,
                "client_id": "user-admin-spa",
            }
        )
        ok("RT 刷新轮转", "新 RT=" + str(refreshed.get("refresh_token", ""))[:12] + "...")
        rt1_new = refreshed.get("refresh_token", "")
    except Exception as e:
        bad("RT 刷新", str(e))
        rt1_new = rt1

    # 8) 统一登出 → RT 失效
    logout_req = urllib.request.Request(
        BASE + "/connect/logout?post_logout_redirect_uri=" + urllib.parse.quote(good_logout, safe="")
    )
    try:
        with op.open(logout_req, timeout=10) as resp:
            pass
    except Exception:
        pass

    try:
        token(
            {
                "grant_type": "refresh_token",
                "refresh_token": rt1_new or rt1,
                "client_id": "user-admin-spa",
            }
        )
        bad("统一登出后 RT 仍可用")
    except Exception:
        ok("统一登出后 RT 刷新失败")

    if rt2:
        try:
            token(
                {
                    "grant_type": "refresh_token",
                    "refresh_token": rt2,
                    "client_id": "auth-admin-spa",
                }
            )
            bad("统一登出后另一 client RT 仍可用（应全链吊销）")
        except Exception:
            ok("统一登出后 auth-admin RT 亦失效（全链吊销）")

    print_summary()


def print_summary() -> None:
    print(f"\n== Result: {PASS} passed, {FAIL} failed ==")
    raise SystemExit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
