<template>
  <div class="ask-page">
    <div class="ask-header">
      <h2>问知识库</h2>
      <div class="login-area">
        <template v-if="loggedIn">
          <span class="ok">已登录</span>
          <el-button link @click="onLogout">退出</el-button>
        </template>
        <el-button v-else type="primary" size="small" @click="onLogin">登录后提问</el-button>
      </div>
    </div>

    <div class="messages" ref="listRef">
      <div v-if="!messages.length" class="empty">
        基于站内文章与站内知识的 RAG 问答；回答附带出处引用。
      </div>
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <div class="bubble">
          <div class="text">{{ m.text }}<span v-if="m.streaming" class="cursor">▌</span></div>
          <div v-if="m.mode === 'KEYWORD_FALLBACK'" class="fallback-tip">
            ⚠ AI 组织暂不可用，已降级为关键词检索结果
          </div>
          <div v-if="m.refs && m.refs.length" class="refs">
            <div class="refs-title">引用来源</div>
            <div v-for="r in m.refs" :key="r.chunkId" class="ref-item">
              <a v-if="r.docUrl" :href="r.docUrl" target="_blank">{{ r.docTitle }}</a>
              <span v-else>{{ r.docTitle }}<span class="in-site">（站内知识）</span></span>
              <span class="score">{{ r.score }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="input-area">
      <el-input
        v-model="draft"
        type="textarea"
        :rows="2"
        maxlength="500"
        show-word-limit
        placeholder="输入问题（1–500 字），Ctrl+Enter 发送"
        @keydown.ctrl.enter.prevent="send"
      />
      <div class="input-actions">
        <el-button
          v-if="streaming"
          type="warning"
          plain
          size="small"
          @click="stop"
        >停止生成</el-button>
        <el-button
          v-else
          type="primary"
          :disabled="!draft.trim() || !loggedIn"
          @click="send"
        >发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { clearTokens, hasValidAccessToken, logout, redirectForLogin } from '@/utils/oidc'
import { streamAsk, type AskFrame, type AskRefs } from '@/api/ask'

interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  refs?: AskRefs[]
  mode?: string
  streaming?: boolean
}

const loggedIn = ref(hasValidAccessToken())
const draft = ref('')
const streaming = ref(false)
const messages = ref<ChatMessage[]>([])
const sessionId = ref<string | undefined>(undefined)
const listRef = ref<HTMLElement>()
let abort: AbortController | null = null
let loginTimer: ReturnType<typeof setInterval> | null = null

loginTimer = setInterval(() => {
  loggedIn.value = hasValidAccessToken()
}, 3000)

function onLogin() {
  void redirectForLogin('/ask')
}

function onLogout() {
  logout()
}

function stop() {
  abort?.abort()
  abort = null
  streaming.value = false
  const last = messages.value[messages.value.length - 1]
  if (last?.streaming) last.streaming = false
}

async function send() {
  const question = draft.value.trim()
  if (!question || streaming.value || !loggedIn.value) return
  if (question.length > 500) {
    ElMessage.warning('问题不能超过 500 字')
    return
  }
  draft.value = ''
  messages.value.push({ role: 'user', text: question })
  const answer: ChatMessage = { role: 'assistant', text: '', streaming: true }
  messages.value.push(answer)
  streaming.value = true
  await scrollToBottom()

  abort = new AbortController()
  try {
    await streamAsk(
      question,
      sessionId.value,
      {
        onFrame: (frame: AskFrame) => {
          if (frame.type === 'meta') {
            sessionId.value = frame.sessionId
            answer.mode = frame.mode
          } else if (frame.type === 'delta') {
            answer.text += frame.text ?? ''
            void scrollToBottom()
          } else if (frame.type === 'refs') {
            answer.refs = frame.refs ?? []
          } else if (frame.type === 'done') {
            answer.streaming = false
          } else if (frame.type === 'error') {
            answer.streaming = false
            ElMessage.error(`生成中断：${frame.msg ?? '未知错误'}（已保留已生成内容）`)
          }
        },
        onPreStreamError: (status, code, msg) => {
          answer.streaming = false
          if (status === 401 || code === 10001) {
            clearTokens()
            loggedIn.value = false
            ElMessage.warning('请先登录')
          } else if (status === 429) {
            ElMessage.warning(msg || '请求过于频繁')
          } else {
            ElMessage.error(`${msg || '请求失败'}（${status}）`)
          }
          // 移除空回答气泡
          if (!answer.text) messages.value.pop()
        },
        onDone: () => {
          answer.streaming = false
          streaming.value = false
        },
      },
      abort.signal,
    )
  } catch (e) {
    if ((e as Error).name !== 'AbortError') {
      ElMessage.error('连接中断，请重试')
    }
  } finally {
    streaming.value = false
    abort = null
  }
}

async function scrollToBottom() {
  await nextTick()
  listRef.value?.scrollTo({ top: listRef.value.scrollHeight })
}

onUnmounted(() => {
  abort?.abort()
  if (loginTimer) clearInterval(loginTimer)
})
</script>

<style scoped>
.ask-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  max-width: 860px;
  margin: 0 auto;
  gap: 12px;
}
.ask-header { display: flex; align-items: center; justify-content: space-between; }
.login-area { display: flex; align-items: center; gap: 8px; }
.login-area .ok { color: var(--el-color-success); font-size: 13px; }
.messages { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 12px; padding: 8px; }
.empty { color: var(--el-text-color-secondary); text-align: center; margin-top: 48px; }
.msg { display: flex; }
.msg.user { justify-content: flex-end; }
.bubble {
  max-width: 78%;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  padding: 10px 14px;
  background: var(--el-bg-color);
  white-space: pre-wrap;
  word-break: break-word;
}
.msg.user .bubble { background: var(--el-color-primary-light-9); }
.cursor { animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.fallback-tip { color: var(--el-color-warning); font-size: 12px; margin-top: 6px; }
.refs { margin-top: 10px; border-top: 1px dashed var(--el-border-color); padding-top: 8px; }
.refs-title { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 4px; }
.ref-item { font-size: 13px; display: flex; gap: 8px; align-items: baseline; }
.ref-item a { color: var(--el-color-primary); }
.ref-item .in-site { color: var(--el-color-success); font-size: 12px; }
.ref-item .score { color: var(--el-text-color-secondary); font-size: 12px; }
.input-area { display: flex; flex-direction: column; gap: 8px; }
.input-actions { display: flex; justify-content: flex-end; }
</style>
