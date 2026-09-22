/**
 * 统一认证 SSO（禁止在 blog 自建账密登录）。
 * 流程：跳 /auth/sso/authorize → 回跳 /sso/callback?code= → POST /auth/sso/token
 */
export const SSO_CLIENT_ID = 'blog-admin'

export function buildAuthorizeUrl(returnPath = '/sso/callback'): string {
  const origin = window.location.origin
  const returnUrl = `${origin}${returnPath}`
  return (
    `/auth/sso/authorize?client_id=${encodeURIComponent(SSO_CLIENT_ID)}` +
    `&return_url=${encodeURIComponent(returnUrl)}`
  )
}

export function redirectToSso(): void {
  window.location.href = buildAuthorizeUrl()
}
