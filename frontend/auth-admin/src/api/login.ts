import type { LoginOutcome } from '@/types'

/** 登录失败统一文案：不泄露账号是否存在、是否锁定 */
export const LOGIN_FAIL_MESSAGE = '用户名或密码错误'

/** 账密登录：POST /login（form-login，withCredentials），成功后后端 Set-Cookie AUTH_SSO_SESSION */
export async function login(username: string, password: string): Promise<LoginOutcome> {
  const body = new URLSearchParams({ username, password })
  try {
    const response = await fetch('/login', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
      redirect: 'manual',
    })
    if (response.type === 'opaqueredirect' || (response.status >= 200 && response.status < 400)) {
      return { ok: true }
    }
    return { ok: false, message: LOGIN_FAIL_MESSAGE }
  } catch {
    return { ok: false, message: LOGIN_FAIL_MESSAGE }
  }
}
