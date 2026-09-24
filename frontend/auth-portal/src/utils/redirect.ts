/** 解析 return_url：站内相对路径，或统一登录门面/IdP（localhost:9080）上的绝对 URL。防开放重定向。 */
const ALLOWED_ORIGINS = [
  'http://localhost:9080',
  'http://127.0.0.1:9080',
  'https://auth.example.local',
]

export function resolveReturnUrl(raw: string | null): string {
  if (!raw) return ''
  const value = raw.trim()
  if (value.startsWith('/') && !value.startsWith('//')) {
    return value
  }
  try {
    const url = new URL(value)
    if (ALLOWED_ORIGINS.includes(url.origin)) {
      return url.toString()
    }
  } catch {
    /* invalid URL */
  }
  return ''
}

export function afterLoginRedirect(returnUrl: string): void {
  if (returnUrl) {
    window.location.href = returnUrl
    return
  }
  window.location.href = '/'
}
