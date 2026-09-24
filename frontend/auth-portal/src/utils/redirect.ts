/** 解析 return_url：仅允许站内相对路径，防开放重定向 */
export function resolveReturnUrl(raw: string | null): string {
  if (!raw) return ''
  const value = raw.trim()
  if (!value.startsWith('/') || value.startsWith('//')) return ''
  return value
}

export function afterLoginRedirect(returnUrl: string): void {
  if (returnUrl) {
    window.location.href = returnUrl
    return
  }
  window.location.href = '/'
}
