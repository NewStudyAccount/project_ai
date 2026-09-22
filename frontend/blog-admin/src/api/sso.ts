import axios from 'axios'
import type { ApiResult } from '@/types'
import { setAccessToken, setRefreshToken, clearTokens } from '@/utils/token'
import { SSO_CLIENT_ID } from '@/utils/sso'

export interface SsoTokenVo {
  accessToken: string
  refreshToken: string
  accessExpiresIn: number
  userId: number | string | null
  username: string
  realName: string
}

/** 用 SSO 一次性 code 换 JWT（调统一认证，不落库密码）。 */
export async function exchangeSsoCode(code: string): Promise<SsoTokenVo> {
  try {
    const res = await axios.post<ApiResult<SsoTokenVo>>('/auth/sso/token', {
      code,
      clientId: SSO_CLIENT_ID,
    })
    const body = res.data
    if (body.code !== 0 || !body.data) {
      throw new Error(body.msg || 'SSO 换票失败')
    }
    setAccessToken(body.data.accessToken)
    setRefreshToken(body.data.refreshToken)
    return body.data
  } catch (e) {
    clearTokens()
    throw e
  }
}
