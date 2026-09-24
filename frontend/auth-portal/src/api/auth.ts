import type { LoginOutcome } from '@/types'

/** 登录失败统一文案：不泄露账号是否存在、是否锁定 */
export const LOGIN_FAIL_MESSAGE = '用户名或密码错误'

/**
 * 账密登录：POST /api/login（form-login，经 Vite /api 代理到 auth-service）。
 * 成功由后端 Set-Cookie AUTH_SSO_SESSION；前端不读、不写该 Cookie。
 * 注意：页面路由是 GET /login，验密是 POST /api/login，二者不可混用。
 */
export async function login(username: string, password: string): Promise<LoginOutcome> {
  const body = new URLSearchParams({ username, password })
  try {
    const response = await fetch('/api/login', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
      redirect: 'manual',
    })
    // form-login 成功为 302（opaqueredirect）；2xx 视为已建立会话
    if (response.type === 'opaqueredirect' || (response.status >= 200 && response.status < 400)) {
      return { ok: true }
    }
    return { ok: false, message: LOGIN_FAIL_MESSAGE }
  } catch {
    return { ok: false, message: LOGIN_FAIL_MESSAGE }
  }
}
