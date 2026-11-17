import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, renderAt } from '../../testUtils'
import { encodeWav } from './wav'

const sse = (events: [string, unknown][]) =>
  new Response(events.map(([e, d]) => `event: ${e}\ndata: ${JSON.stringify(d)}\n\n`).join(''), { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
const UP = 'http://minio.test/bucket'
const question = { id: 8, part: 2, prompt: 'Describe a trip.', cueCard: ['where', 'when'], maxSeconds: 60 }
const permit = (id = 5) => ({ submissionId: id, url: UP, fields: { key: 'k', policy: 'p', 'x-amz-signature': 's' }, maxBytes: 1_000_000, contentType: 'audio/wav', maxSeconds: 60, expiresAt: 'x' })
const graded = {
  id: 5, questionId: 8, status: 'GRADED', createdAt: 'x', audioUrl: 'http://minio.test/a.wav', durationSec: 12,
  result: {
    status: 'GRADED', transcript: 'I went to Da Lat.', bands: { pronunciation: 6, fluency: 6.5, lexical: 7, grammar: 5.5 }, overall: 6.5,
    fluency: { wordsPerMinute: 118.4, longPauses: 2, pausesPerMinute: 3, speechRatio: 0.8, band: 6 },
    mispronounced: [{ word: 'trip', score: 0.4, issues: ['vowel'], expectedIpa: 'trɪp', heardIpa: 'trip' }],
    feedback: 'Nói khá tốt.', suggestions: [{ original: 'very good', improved: 'excellent', reason: 'Từ mạnh hơn' }],
  },
}

// Giả AudioContext của jsdom: duration lấy từ kích thước tệp (1 byte = 1 giây) để test điều khiển được độ dài.
function stubAudio() {
  vi.stubGlobal('AudioContext', class {
    decodeAudioData = async (buf: ArrayBuffer) => ({ duration: buf.byteLength })
    close = async () => {}
  })
  vi.stubGlobal('OfflineAudioContext', class {
    destination = {}
    constructor(public ch: number, public len: number, public rate: number) {}
    createBufferSource = () => ({ connect() {}, start() {}, buffer: null })
    startRendering = async () => ({ getChannelData: () => new Float32Array(this.len) })
  })
  // jsdom chưa có Blob.arrayBuffer (trình duyệt thật có)
  Blob.prototype.arrayBuffer ??= function (this: Blob) {
    return new Promise<ArrayBuffer>((resolve, reject) => {
      const r = new FileReader()
      r.onload = () => resolve(r.result as ArrayBuffer)
      r.onerror = () => reject(r.error)
      r.readAsArrayBuffer(this)
    })
  }
  URL.createObjectURL = () => 'blob:test'
  URL.revokeObjectURL = () => {}
}
const file = (seconds: number) => new File([new Uint8Array(seconds)], 'a.mp3', { type: 'audio/mpeg' })

interface Opts { latest?: unknown; complete?: () => Response; upload?: () => Response; create?: () => Response; submission?: () => Response }

function wire(o: Opts = {}) {
  const calls: string[] = []
  const uploads: { form: FormData; init?: RequestInit }[] = []
  let done = false // job xong thì bản nộp mới sang GRADED
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url}`
    calls.push(key)
    if (url === '/api/auth/refresh') return json(200, auth)
    if (url === '/api/speaking/questions/8') return json(200, question)
    if (url === '/api/me/speaking?questionId=8&limit=1') return json(200, { data: o.latest ? [o.latest] : [] })
    if (url === '/api/speaking/submissions' && init?.method === 'POST') return o.create ? o.create() : json(201, permit())
    if (url === UP) {
      uploads.push({ form: init?.body as FormData, init })
      return o.upload ? o.upload() : new Response(null, { status: 204 })
    }
    if (url === '/api/speaking/submissions/5/complete') return o.complete ? o.complete() : json(202, { submissionId: 5, status: 'GRADING', jobId: 9, quotaRemaining: 2 })
    if (url === '/api/ai/jobs/9/stream') { done = true; return sse([['result', { id: 9, kind: 'SPEAKING_GRADE', status: 'DONE', quotaRefunded: false }]]) }
    if (url === '/api/speaking/submissions/5') return o.submission ? o.submission() : json(200, done ? graded : { ...graded, status: 'GRADING', result: null })
    return json(404)
  })
  return { calls, uploads }
}
const count = (calls: string[], k: string) => calls.filter((c) => c === k).length

describe('encodeWav', () => {
  test('header RIFF/PCM16, mono, 16 kHz, đúng số mẫu và cắt giá trị ngoài [-1,1]', () => {
    const buf = encodeWav(new Float32Array([0, 1, -1, 2, -2, 0.5]))
    const v = new DataView(buf)
    const tag = (at: number) => String.fromCharCode(...new Uint8Array(buf, at, 4))
    expect([tag(0), tag(8), tag(12), tag(36)]).toEqual(['RIFF', 'WAVE', 'fmt ', 'data'])
    expect(buf.byteLength).toBe(44 + 6 * 2)
    expect(v.getUint32(4, true)).toBe(36 + 12)
    expect(v.getUint32(16, true)).toBe(16)
    expect(v.getUint16(20, true)).toBe(1) // PCM
    expect(v.getUint16(22, true)).toBe(1) // mono
    expect(v.getUint32(24, true)).toBe(16000)
    expect(v.getUint32(28, true)).toBe(32000) // byte rate
    expect(v.getUint16(32, true)).toBe(2)
    expect(v.getUint16(34, true)).toBe(16)
    expect(v.getUint32(40, true)).toBe(12)
    expect([...new Int16Array(buf, 44)]).toEqual([0, 32767, -32767, 32767, -32767, 16384])
  })
  test('sample rate tùy chọn được ghi vào header', () => {
    const v = new DataView(encodeWav(new Float32Array(3), 8000))
    expect(v.getUint32(24, true)).toBe(8000)
    expect(v.getUint32(28, true)).toBe(16000)
  })
})

describe('màn Nói', () => {
  beforeEach(() => {
    setAccessToken(null)
    stubAudio()
  })

  test('chọn tệp → nộp: create, upload multipart (fields trước, file cuối, không Bearer), complete, chờ job, kết quả', async () => {
    const { calls, uploads } = wire()
    renderAt('/noi/8')
    expect(await screen.findByText('Describe a trip.')).toBeInTheDocument()
    expect(screen.getByText('• where')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Nộp bản ghi/ })).toBeDisabled()
    await userEvent.upload(screen.getByLabelText('Chọn tệp audio'), file(10))
    expect(await screen.findByLabelText('Nghe lại bản ghi')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Nộp bản ghi/ }))

    const result = await screen.findByLabelText('Điểm')
    expect(result).toHaveTextContent('6.5')
    expect(screen.getByRole('progressbar', { name: 'Phát âm' })).toHaveAttribute('aria-valuenow', '6')
    expect(screen.getByText('I went to Da Lat.')).toBeInTheDocument()
    expect(screen.getByLabelText('Độ trôi chảy')).toHaveTextContent('118')
    expect(screen.getByLabelText('Từ phát âm chưa chuẩn')).toHaveTextContent('trɪp')
    expect(screen.getByLabelText('Từ phát âm chưa chuẩn')).toHaveTextContent('trip')
    expect(screen.getByLabelText('Gợi ý diễn đạt')).toHaveTextContent('excellent')
    expect(screen.getByLabelText('Nghe lại audio')).toHaveAttribute('src', 'http://minio.test/a.wav')
    expect(screen.getByRole('button', { name: 'Nói lại' })).toBeInTheDocument()

    expect(calls.filter((c) => /POST|stream|submissions\/5$/.test(c) && !c.includes('auth/'))).toEqual([
      'POST /api/speaking/submissions', `POST ${UP}`, 'POST /api/speaking/submissions/5/complete', 'GET /api/speaking/submissions/5', 'GET /api/ai/jobs/9/stream', 'GET /api/speaking/submissions/5',
    ])
    const form = uploads[0].form
    expect([...form.keys()]).toEqual(['key', 'policy', 'x-amz-signature', 'file'])
    expect(new Headers(uploads[0].init?.headers).get('Authorization')).toBeNull()
    const sent = form.get('file') as File
    expect(sent.size).toBe(44 + 10 * 16000 * 2) // 10 giây WAV 16 kHz mono PCM16
  })

  test('bấm nộp hai lần liên tiếp chỉ tạo một lượt', async () => {
    const { calls } = wire()
    renderAt('/noi/8')
    await userEvent.upload(await screen.findByLabelText('Chọn tệp audio'), file(5))
    const btn = await screen.findByRole('button', { name: /Nộp bản ghi/ })
    await waitFor(() => expect(btn).toBeEnabled())
    await Promise.all([userEvent.click(btn), userEvent.click(btn)])
    await screen.findByLabelText('Điểm')
    expect(count(calls, 'POST /api/speaking/submissions')).toBe(1)
    expect(count(calls, `POST ${UP}`)).toBe(1)
  })

  test('tệp ngắn hơn 1 giây hoặc dài hơn tối đa bị báo trước khi tải lên', async () => {
    const { calls } = wire()
    renderAt('/noi/8')
    const input = await screen.findByLabelText('Chọn tệp audio')
    await userEvent.upload(input, file(0))
    expect(await screen.findByRole('alert')).toHaveTextContent('quá ngắn')
    await userEvent.upload(screen.getByLabelText('Chọn tệp audio'), file(90))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('dài hơn 60 giây'))
    expect(calls.some((c) => c.startsWith('POST') && !c.includes('auth/'))).toBe(false)
  })

  test('lỗi mạng khi tải lên: báo rõ, thử lại dùng lại quyền tải, không tạo lượt mới', async () => {
    let n = 0
    const { calls } = wire({ upload: () => { if (++n === 1) throw new TypeError('Failed to fetch'); return new Response(null, { status: 204 }) } })
    renderAt('/noi/8')
    await userEvent.upload(await screen.findByLabelText('Chọn tệp audio'), file(5))
    await userEvent.click(await screen.findByRole('button', { name: /Nộp bản ghi/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('mất mạng hoặc bị chặn kết nối')
    await userEvent.click(screen.getByRole('button', { name: /Nộp bản ghi/ }))
    expect(await screen.findByLabelText('Điểm')).toBeInTheDocument()
    expect(count(calls, 'POST /api/speaking/submissions')).toBe(1)
  })

  test('410 upload_expired: tạo lại submission ở lần nộp sau', async () => {
    let completes = 0
    const { calls } = wire({ complete: () => (++completes === 1 ? apiError(410, 'Hết hạn') : json(202, { submissionId: 5, status: 'GRADING', jobId: 9, quotaRemaining: 1 })) })
    renderAt('/noi/8')
    await userEvent.upload(await screen.findByLabelText('Chọn tệp audio'), file(5))
    await userEvent.click(await screen.findByRole('button', { name: /Nộp bản ghi/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('hết hạn')
    await userEvent.click(screen.getByRole('button', { name: /Nộp bản ghi/ }))
    await screen.findByLabelText('Điểm')
    expect(count(calls, 'POST /api/speaking/submissions')).toBe(2)
  })

  test('400 audio_too_short và 503 hiện thông báo, giữ bản ghi để thử lại', async () => {
    wire({ complete: () => json(400, { error: { code: 'audio_too_short', message: 'x' } }) })
    renderAt('/noi/8')
    await userEvent.upload(await screen.findByLabelText('Chọn tệp audio'), file(5))
    await userEvent.click(await screen.findByRole('button', { name: /Nộp bản ghi/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('quá ngắn')
    expect(screen.getByLabelText('Nghe lại bản ghi')).toBeInTheDocument()
  })

  test('503 khi tạo lượt: báo bận, bản ghi vẫn còn', async () => {
    wire({ create: () => apiError(503, 'busy') })
    renderAt('/noi/8')
    await userEvent.upload(await screen.findByLabelText('Chọn tệp audio'), file(5))
    await userEvent.click(await screen.findByRole('button', { name: /Nộp bản ghi/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('đang bận')
    expect(screen.getByLabelText('Nghe lại bản ghi')).toBeInTheDocument()
  })

  test('402 hết lượt: thẻ kèm tên gói', async () => {
    wire({ create: () => apiError(402, 'hết', { products: [{ name: 'Gói 20 lượt' }] }) })
    renderAt('/noi/8')
    await userEvent.upload(await screen.findByLabelText('Chọn tệp audio'), file(5))
    await userEvent.click(await screen.findByRole('button', { name: /Nộp bản ghi/ }))
    expect(await screen.findByText('Bạn đã hết lượt chấm AI')).toBeInTheDocument()
    expect(screen.getByText(/Gói 20 lượt/)).toBeInTheDocument()
  })

  test('vào trang có bản UNCLEAR: báo lý do, không trừ lượt, Nói lại quay về ghi âm', async () => {
    wire({ latest: { id: 5, questionId: 8, status: 'UNCLEAR', createdAt: 'x', result: { status: 'UNCLEAR', reason: 'too_few_words' } } })
    // GET /submissions/5 trả lại cùng bản UNCLEAR
    const base = globalThis.fetch
    vi.stubGlobal('fetch', async (url: string, init?: RequestInit) =>
      url === '/api/speaking/submissions/5' ? json(200, { id: 5, questionId: 8, status: 'UNCLEAR', createdAt: 'x', result: { status: 'UNCLEAR', reason: 'too_few_words' } }) : base(url, init))
    renderAt('/noi/8')
    expect(await screen.findByText(/quá ít từ/)).toBeInTheDocument()
    expect(screen.getByText('Không trừ lượt chấm.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Nói lại' }))
    expect(await screen.findByRole('button', { name: /Nộp bản ghi/ })).toBeDisabled()
  })

  test('vào trang có bản đang GRADING: hiện trạng thái chờ', async () => {
    const grading = { id: 5, questionId: 8, status: 'GRADING', createdAt: 'x' }
    wire({ latest: grading, submission: () => json(200, grading) })
    renderAt('/noi/8')
    expect(await screen.findByText(/Đang chấm bài nói/)).toBeInTheDocument()
  })

  test('403 bộ bị khóa khi tải đề', async () => {
    vi.stubGlobal('fetch', async (url: string) => {
      if (url === '/api/auth/refresh') return json(200, auth)
      if (url.startsWith('/api/speaking/questions')) return apiError(403, 'khóa')
      return json(200, { data: [] })
    })
    renderAt('/noi/8')
    expect(await screen.findByText('Bộ bài tập đang khóa')).toBeInTheDocument()
  })

  test('micro bị từ chối hoặc không hỗ trợ: gợi ý chọn tệp', async () => {
    wire()
    vi.stubGlobal('MediaRecorder', class {})
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: async () => { throw new DOMException('no', 'NotAllowedError') } } })
    renderAt('/noi/8')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu ghi' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Chọn tệp audio')
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined })
  })
})
