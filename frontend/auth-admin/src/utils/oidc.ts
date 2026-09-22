import { createPkce, randomString } from './pkce'
import { saveTokens, clearTokens } from './token'

/** auth-admin 独立 public + PKCE Client（路线 1） */
export const OIDC_CONFIG = {
  clientId: 'auth-admin',
  redirectUri: `${window.location.origin}/callback`,
  authorizeEndpoint: '/oauth2/authorize',
  tokenEndpoint: '/oauth2/token',
  scope: 'openid profile',
}

const STATE_KEY = 'auth_admin_oidc_state'
const VERIFIER_KEY = 'auth_admin_pkce_verifier'

export async function buildAuthorizeUrl(): Promise<string> {
  const { verifier, challenge } = await createPkce()
  const state = randomString(32)
  sessionStorage.setItem(STATE_KEY, state)
  sessionStorage.setItem(VERIFIER_KEY, verifier)
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: OIDC_CONFIG.clientId,
    redirect_uri: OIDC_CONFIG.redirectUri,
    scope: OIDC_CONFIG.scope,
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  })
  return `${OIDC_CONFIG.authorizeEndpoint}?${params.toString()}`
}

export async function handleCallback(code: string, state: string): Promise<void> {
  const expected = sessionStorage.getItem(STATE_KEY)
  const verifier = sessionStorage.getItem(VERIFIER_KEY)
  if (!expected || expected !== state || !verifier) {
    clearTokens()
    throw new Error('state 或 PKCE 校验失败')
  }
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: OIDC_CONFIG.redirectUri,
    client_id: OIDC_CONFIG.clientId,
    code_verifier: verifier,
  })
  const res = await fetch(OIDC_CONFIG.tokenEndpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  const json = (await res.json()) as {
    access_token: string
    refresh_token: string
    id_token?: string
  }
  if (!res.ok || !json.access_token) {
    throw new Error('换票失败')
  }
  saveTokens(json.access_token, json.refresh_token, json.id_token)
  sessionStorage.removeItem(STATE_KEY)
  sessionStorage.removeItem(VERIFIER_KEY)
}
