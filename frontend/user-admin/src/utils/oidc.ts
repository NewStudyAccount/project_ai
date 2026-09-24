/** OIDC RP（public + PKCE S256）— 唯一登录门面在 auth-portal，本模块只负责跳转与换票。 */

export interface OidcTokens {
  accessToken: string
  refreshToken?: string
  idToken?: string
  expiresAt: number
}

export interface OidcConfig {
  issuer: string
  clientId: string
  redirectUri: string
  postLogoutRedirectUri: string
  scope: string
}

const STORAGE = {
  verifier: 'oidc:code_verifier',
  state: 'oidc:state',
  nonce: 'oidc:nonce',
  returnTo: 'oidc:return_to',
  tokens: 'oidc:tokens',
} as const

export const oidcConfig: OidcConfig = {
  // 必须与 SSO Cookie 主机一致（cookie 忽略端口）；统一 localhost，禁止混用 127.0.0.1
  issuer: (import.meta.env.VITE_OIDC_ISSUER as string | undefined) || 'http://localhost:9080',
  clientId: (import.meta.env.VITE_OIDC_CLIENT_ID as string | undefined) || 'user-admin-spa',
  redirectUri:
    (import.meta.env.VITE_OIDC_REDIRECT_URI as string | undefined) ||
    `${window.location.origin}/callback`,
  postLogoutRedirectUri:
    (import.meta.env.VITE_OIDC_POST_LOGOUT_REDIRECT_URI as string | undefined) ||
    `${window.location.origin}/logged-out`,
  scope: 'openid profile',
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = ''
  bytes.forEach((b) => {
    binary += String.fromCharCode(b)
  })
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

async function createPkce(): Promise<{ verifier: string; challenge: string }> {
  const random = crypto.getRandomValues(new Uint8Array(32))
  const verifier = base64UrlEncode(random)
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  return { verifier, challenge: base64UrlEncode(new Uint8Array(digest)) }
}

function randomToken(): string {
  return base64UrlEncode(crypto.getRandomValues(new Uint8Array(16)))
}

export function getTokens(): OidcTokens | null {
  const raw = sessionStorage.getItem(STORAGE.tokens)
  if (!raw) return null
  try {
    return JSON.parse(raw) as OidcTokens
  } catch {
    return null
  }
}

export function setTokens(tokens: OidcTokens): void {
  sessionStorage.setItem(STORAGE.tokens, JSON.stringify(tokens))
}

export function clearTokens(): void {
  sessionStorage.removeItem(STORAGE.tokens)
  sessionStorage.removeItem(STORAGE.verifier)
  sessionStorage.removeItem(STORAGE.state)
  sessionStorage.removeItem(STORAGE.nonce)
  sessionStorage.removeItem(STORAGE.returnTo)
}

export function hasValidAccessToken(): boolean {
  const tokens = getTokens()
  return !!tokens?.accessToken && tokens.expiresAt > Date.now() + 5_000
}

/** 方案 1：路由守卫直接顶层跳转 authorize，无本地登录页。 */
export async function redirectForLogin(returnTo?: string): Promise<void> {
  const { verifier, challenge } = await createPkce()
  const state = randomToken()
  const nonce = randomToken()
  sessionStorage.setItem(STORAGE.verifier, verifier)
  sessionStorage.setItem(STORAGE.state, state)
  sessionStorage.setItem(STORAGE.nonce, nonce)
  sessionStorage.setItem(STORAGE.returnTo, returnTo || window.location.pathname + window.location.search)

  const url = new URL('/oauth2/authorize', oidcConfig.issuer)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('client_id', oidcConfig.clientId)
  url.searchParams.set('redirect_uri', oidcConfig.redirectUri)
  url.searchParams.set('scope', oidcConfig.scope)
  url.searchParams.set('state', state)
  url.searchParams.set('nonce', nonce)
  url.searchParams.set('code_challenge', challenge)
  url.searchParams.set('code_challenge_method', 'S256')
  window.location.assign(url.toString())
}

export function consumeReturnTo(): string {
  const value = sessionStorage.getItem(STORAGE.returnTo) || '/'
  sessionStorage.removeItem(STORAGE.returnTo)
  return value.startsWith('/') && !value.startsWith('//') ? value : '/'
}

/** 回调换票；state 不符或无 code 时抛错。 */
export async function exchangeCode(query: URLSearchParams): Promise<OidcTokens> {
  const code = query.get('code')
  const state = query.get('state')
  const expectedState = sessionStorage.getItem(STORAGE.state)
  const verifier = sessionStorage.getItem(STORAGE.verifier)
  sessionStorage.removeItem(STORAGE.state)
  sessionStorage.removeItem(STORAGE.verifier)
  sessionStorage.removeItem(STORAGE.nonce)

  if (!code) {
    throw new Error('缺少授权码')
  }
  if (!state || !expectedState || state !== expectedState) {
    throw new Error('state 校验失败')
  }
  if (!verifier) {
    throw new Error('缺少 code_verifier')
  }

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: oidcConfig.redirectUri,
    client_id: oidcConfig.clientId,
    code_verifier: verifier,
  })
  const response = await fetch(new URL('/oauth2/token', oidcConfig.issuer), {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  if (!response.ok) {
    let detail = ''
    try {
      detail = (await response.text()).slice(0, 200)
    } catch {
      /* ignore */
    }
    throw new Error(`换票失败 HTTP ${response.status} ${detail}`)
  }
  const data = (await response.json()) as {
    access_token: string
    refresh_token?: string
    id_token?: string
    expires_in?: number
  }
  const tokens: OidcTokens = {
    accessToken: data.access_token,
    refreshToken: data.refresh_token,
    idToken: data.id_token,
    expiresAt: Date.now() + (data.expires_in ?? 600) * 1000,
  }
  setTokens(tokens)
  return tokens
}

export async function refreshTokens(): Promise<OidcTokens | null> {
  const current = getTokens()
  if (!current?.refreshToken) return null
  const body = new URLSearchParams({
    grant_type: 'refresh_token',
    refresh_token: current.refreshToken,
    client_id: oidcConfig.clientId,
  })
  const response = await fetch(new URL('/oauth2/token', oidcConfig.issuer), {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  if (!response.ok) {
    clearTokens()
    return null
  }
  const data = (await response.json()) as {
    access_token: string
    refresh_token?: string
    id_token?: string
    expires_in?: number
  }
  const tokens: OidcTokens = {
    accessToken: data.access_token,
    refreshToken: data.refresh_token ?? current.refreshToken,
    idToken: data.id_token ?? current.idToken,
    expiresAt: Date.now() + (data.expires_in ?? 600) * 1000,
  }
  setTokens(tokens)
  return tokens
}

/** 唯一登出：先清本地令牌，再经 IdP /connect/logout（禁止仅本地退出作为主路径）。 */
export function logout(): void {
  clearTokens()
  const url = new URL('/connect/logout', oidcConfig.issuer)
  url.searchParams.set('post_logout_redirect_uri', oidcConfig.postLogoutRedirectUri)
  window.location.assign(url.toString())
}
