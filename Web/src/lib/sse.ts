import { api, ApiError, rawFetch } from './api'

export interface JobView<R = unknown> { id: number; kind: string; status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'; result?: R; error?: string; quotaRefunded: boolean }

export interface SseEvent { event: string; data: string }

// Tách các khối "event:/data:" đã đủ (kết thúc bằng dòng trống); phần dở dang trả lại ở rest.
export function parseSse(buffer: string): { events: SseEvent[]; rest: string } {
  const blocks = buffer.replace(/\r\n/g, '\n').split('\n\n')
  const rest = blocks.pop() ?? ''
  const events: SseEvent[] = []
  for (const block of blocks) {
    let event = 'message'
    const data: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith(':')) continue // keepalive
      const i = line.indexOf(':')
      const field = i < 0 ? line : line.slice(0, i)
      const value = i < 0 ? '' : line.slice(i + 1).replace(/^ /, '')
      if (field === 'event') event = value
      else if (field === 'data') data.push(value)
    }
    if (data.length) events.push({ event, data: data.join('\n') })
  }
  return { events, rest }
}

export class JobTimeout extends Error {}
export const JOB_TIMEOUT_MS = 5 * 60_000
const RETRY_MS = 1500
const isTerminal = (j: JobView) => j.status === 'DONE' || j.status === 'FAILED'
const transient = (s: number) => s === 401 || s === 429 || s >= 500

const sleep = (ms: number, signal: AbortSignal) =>
  new Promise<void>((resolve) => {
    const t = setTimeout(resolve, ms)
    signal.addEventListener('abort', () => { clearTimeout(t); resolve() }, { once: true })
  })

// Đọc một lần luồng SSE; trả job khi gặp result/error, null khi luồng đứt giữa chừng.
async function readStream(id: number, signal: AbortSignal): Promise<JobView | null> {
  const res = await rawFetch(`/ai/jobs/${id}/stream`, { signal, headers: { Accept: 'text/event-stream' } })
  if (!res.ok || !res.body) {
    if (transient(res.status)) return null
    throw new ApiError(res.status, 'stream_failed', 'Không theo dõi được kết quả chấm.')
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) return null
      const parsed = parseSse(buffer + decoder.decode(value, { stream: true }))
      buffer = parsed.rest
      for (const e of parsed.events) {
        if (e.event !== 'result' && e.event !== 'error') continue
        const job = JSON.parse(e.data) as JobView
        if (isTerminal(job)) return job
      }
    }
  } finally {
    void reader.cancel().catch(() => {})
  }
}

// Chờ job xong: SSE trước; đứt kết nối thì GET job rồi mở lại. Quá timeoutMs ném JobTimeout.
export async function waitForJob(id: number, signal: AbortSignal, timeoutMs = JOB_TIMEOUT_MS, retryMs = RETRY_MS): Promise<JobView> {
  const inner = new AbortController()
  let timedOut = false
  const stop = () => inner.abort()
  signal.addEventListener('abort', stop, { once: true })
  const timer = setTimeout(() => { timedOut = true; inner.abort() }, timeoutMs)
  try {
    for (let first = true; !inner.signal.aborted; first = false) {
      try {
        if (!first) {
          const job = await api<JobView>(`/ai/jobs/${id}`)
          if (isTerminal(job)) return job
          await sleep(retryMs, inner.signal)
          if (inner.signal.aborted) break
        }
        const job = await readStream(id, inner.signal)
        if (job) return job
      } catch (e) {
        if (inner.signal.aborted) break
        if (e instanceof ApiError && !transient(e.status)) throw e
        await sleep(retryMs, inner.signal) // lỗi mạng: thử lại
      }
    }
    if (timedOut) throw new JobTimeout()
    throw new DOMException('Aborted', 'AbortError')
  } finally {
    clearTimeout(timer)
    signal.removeEventListener('abort', stop)
  }
}
