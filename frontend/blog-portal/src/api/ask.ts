import { getTokens, hasValidAccessToken, refreshTokens } from '@/utils/oidc'

/** 知识问答 SSE 帧类型（api-design.md §4.2 契约） */
export interface AskRefs {
  chunkId: string
  docId: string
  docTitle: string
  docUrl: string
  score: number
}

export interface AskFrame {
  type: 'meta' | 'delta' | 'refs' | 'done' | 'error'
  mode?: string
  model?: string
  sessionId?: string
  text?: string
  refs?: AskRefs[]
  messageId?: string
  costMs?: number
  refCount?: number
  code?: number
  msg?: string
}

export interface AskCallbacks {
  onFrame: (frame: AskFrame) => void
  /** 流开始前错误（JSON Result：401/403/429/503/30001） */
  onPreStreamError: (status: number, code: number, msg: string) => void
  onDone: () => void
}

/**
 * POST /api/v1/knowledge/ask（SSE 流式）。
 * 手动解析 event/data 帧；流开始前 JSON 错误按状态码分流；客户端中断经 AbortSignal。
 */
export async function streamAsk(
  question: string,
  sessionId: string | undefined,
  callbacks: AskCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  const tokens = getTokens()
  if (!hasValidAccessToken()) {
    const refreshed = await refreshTokens()
    if (!refreshed) {
      callbacks.onPreStreamError(401, 10001, '未认证')
      callbacks.onDone()
      return
    }
  }
  const effective = tokens ?? (await refreshTokens())
  const response = await fetch('/api/v1/knowledge/ask', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream, application/json;q=0.9',
      Authorization: `Bearer ${effective?.accessToken ?? ''}`,
    },
    body: JSON.stringify({ question, sessionId }),
    signal,
  })

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('text/event-stream')) {
    // 流开始前错误：JSON Result
    let code = 10004
    let msg = '请求失败'
    try {
      const body = (await response.json()) as { code: number; msg: string }
      code = body.code
      msg = body.msg
    } catch {
      /* ignore */
    }
    callbacks.onPreStreamError(response.status, code, msg)
    callbacks.onDone()
    return
  }

  const reader = response.body?.getReader()
  if (!reader) {
    callbacks.onPreStreamError(500, 10004, '无响应体')
    callbacks.onDone()
    return
  }
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // 帧以空行分隔
    const parts = buffer.split('\n\n')
    buffer = parts.pop() ?? ''
    for (const raw of parts) {
      const frame = parseFrame(raw)
      if (frame) {
        callbacks.onFrame(frame)
      }
    }
  }
  callbacks.onDone()
}

function parseFrame(raw: string): AskFrame | null {
  const lines = raw.split('\n')
  let event = ''
  let data = ''
  for (const line of lines) {
    if (line.startsWith(':')) continue // 心跳注释帧
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data += line.slice(5).trim()
  }
  if (!event) return null
  try {
    const payload = JSON.parse(data) as Record<string, unknown>
    return { type: event as AskFrame['type'], ...payload } as AskFrame
  } catch {
    return { type: event as AskFrame['type'] }
  }
}
