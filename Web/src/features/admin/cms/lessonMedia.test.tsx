import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../../lib/api'
import { auth, json, renderAt } from '../../../testUtils'
import { uploadVideo } from './questions/upload'

vi.mock('./questions/upload', () => ({ uploadAudio: vi.fn(), uploadImage: vi.fn(), uploadVideo: vi.fn() }))

const err = (status: number, code: string, message: string, details?: unknown) => json(status, { error: { code, message, details } })
const course = { id: 7, slug: 'ielts', title: 'IELTS', description: null, targetType: null, status: 'DRAFT', sequential: true, ielts: false, lessonCompletePercent: 80, examUnlockPercent: 70, examRetakes: 2, deliveryMode: 'SELF_PACED' }
const lesson = { id: 30, topicId: 20, type: 'CONNECTIVE_VIDEO', title: 'Bài 1', durationSec: 120, sortOrder: 0, status: 'DRAFT', videoSource: 'YOUTUBE', videoRef: 'abc', subtitles: [{ lang: 'en', cueCount: 12, updatedAt: 'x' }] }
const tree = (l = lesson) => ({
  course,
  levels: [{ level: { id: 10, courseId: 7, code: 'A1', title: 'Sơ cấp', sortOrder: 0, status: 'DRAFT' }, topics: [{ topic: { id: 20, levelId: 10, title: 'Chào hỏi', sortOrder: 0, isFreeTrial: false, skill: 'LISTENING', status: 'DRAFT' }, lessons: [l] }] }],
})

interface Call { url: string; method: string; body: unknown }
function mock(handlers: Record<string, (body: unknown) => Response> = {}) {
  const calls: Call[] = []
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET'
    if (url === '/api/auth/refresh') return json(200, auth)
    if (url === '/api/me/permissions') return json(200, { permissions: ['cms.content'] })
    const body = init?.body instanceof FormData ? init.body : init?.body ? JSON.parse(String(init.body)) : undefined
    calls.push({ url, method, body })
    return (handlers[`${method} ${url}`] ?? (() => json(404)))(body)
  })
  return calls
}
const base = { 'GET /api/admin/cms/courses/7': () => json(200, tree()), 'GET /api/admin/cms/lessons/30/glossary': () => json(200, []) }
const open = () => renderAt('/quan-tri/cms/khoa-hoc/7/bai-giang/30')
const vtt = new File(['WEBVTT'], 'a.vtt', { type: 'text/vtt' })

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

describe('CMS bài giảng: video và phụ đề', () => {
  test('tải phụ đề tiếng Anh gửi PUT multipart, dòng Việt chưa có thì không hiện số câu', async () => {
    const calls = mock({ ...base, 'PUT /api/admin/cms/lessons/30/subtitles/en': () => json(200, { lang: 'en', cueCount: 3, updatedAt: 'x' }) })
    open()
    expect(await screen.findByText('12 câu phụ đề')).toBeInTheDocument()
    expect(screen.getAllByText(/câu phụ đề/)).toHaveLength(1)
    await userEvent.upload(screen.getByLabelText('Tiếng Anh'), vtt)
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT' && c.body instanceof FormData && (c.body.get('file') as File).name === 'a.vtt')).toBe(true))
  })

  test('lỗi 400 của tệp hiện dưới ô', async () => {
    mock({ ...base, 'PUT /api/admin/cms/lessons/30/subtitles/vi': () => err(400, 'validation', 'Dữ liệu không hợp lệ', { file: 'Phụ đề quá 5000 câu' }) })
    open()
    await userEvent.upload(await screen.findByLabelText('Tiếng Việt'), vtt)
    expect(await screen.findByText('Phụ đề quá 5000 câu')).toBeInTheDocument()
  })

  test('xóa phụ đề cần xác nhận', async () => {
    const calls = mock({ ...base, 'DELETE /api/admin/cms/lessons/30/subtitles/en': () => json(204) })
    open()
    await userEvent.click(await screen.findByRole('button', { name: 'Xóa phụ đề Tiếng Anh' }))
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xóa phụ đề Tiếng Anh' }))
    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true))
  })

  test('tải video lên rồi PATCH videoSource STORAGE kèm videoMediaId', async () => {
    vi.mocked(uploadVideo).mockImplementation(async (_f, onProgress) => (onProgress(50), { id: 77 }))
    const calls = mock({ ...base, 'PATCH /api/admin/cms/lessons/30': () => json(200, lesson) })
    open()
    await userEvent.click(await screen.findByLabelText('Tải video lên'))
    await userEvent.upload(screen.getByLabelText('Chọn tệp video (mp4)'), new File(['x'], 'v.mp4', { type: 'video/mp4' }))
    await waitFor(() => expect(calls.find((c) => c.method === 'PATCH')?.body).toEqual({ videoSource: 'STORAGE', videoMediaId: 77 }))
    expect(vi.mocked(uploadVideo).mock.calls[0][2]).toBe('/admin/cms/media/lesson-video')
  })

  test('bài STORAGE hiện "Video đã tải lên" và xem thử', async () => {
    mock({ ...base, 'GET /api/admin/cms/courses/7': () => json(200, tree({ ...lesson, videoSource: 'STORAGE', videoRef: 'k', videoUrl: 'https://s/v.mp4' } as never)) })
    open()
    expect(await screen.findByText(/Video đã tải lên/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Xem thử' })).toHaveAttribute('href', 'https://s/v.mp4')
  })
})

describe('CMS bài giảng: từ nối và giải nghĩa', () => {
  const item = { id: 5, phrase: 'however', partOfSpeech: 'adv', meaningVi: 'tuy nhiên', example: null, source: 'WIKTIONARY' }

  test('liệt kê mục, hiện nhãn Wiktionary', async () => {
    mock({ ...base, 'GET /api/admin/cms/lessons/30/glossary': () => json(200, [item]) })
    open()
    expect(await screen.findByText('tuy nhiên')).toBeInTheDocument()
    expect(screen.getByText('Wiktionary')).toBeInTheDocument()
  })

  test('gợi ý nghĩa điền sẵn và gửi source WIKTIONARY', async () => {
    const calls = mock({
      ...base,
      'GET /api/admin/cms/dictionary?q=environment': () => json(200, [{ word: 'environment', partOfSpeech: 'noun', meaningVi: 'môi trường', example: 'Protect it.' }]),
      'POST /api/admin/cms/lessons/30/glossary': () => json(201, { ...item, id: 6 }),
    })
    open()
    await userEvent.type(await screen.findByLabelText('Từ hoặc cụm từ'), 'environment')
    await userEvent.click(screen.getByRole('button', { name: 'Gợi ý nghĩa' }))
    await userEvent.click(await screen.findByRole('button', { name: /môi trường/ }))
    expect(screen.getByLabelText('Nghĩa tiếng Việt')).toHaveValue('môi trường')
    await userEvent.click(screen.getByRole('button', { name: 'Thêm mục' }))
    await waitFor(() => expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ phrase: 'environment', partOfSpeech: 'noun', meaningVi: 'môi trường', example: 'Protect it.', source: 'WIKTIONARY' }))
  })

  test('tự gõ nghĩa gửi MANUAL; 409 hiện thông điệp', async () => {
    const calls = mock({ ...base, 'POST /api/admin/cms/lessons/30/glossary': () => err(409, 'glossary_duplicate', 'Cụm từ đã có trong bài') })
    open()
    await userEvent.type(await screen.findByLabelText('Từ hoặc cụm từ'), 'so')
    await userEvent.type(screen.getByLabelText('Nghĩa tiếng Việt'), 'vì vậy')
    await userEvent.click(screen.getByRole('button', { name: 'Thêm mục' }))
    expect(await screen.findByText('Cụm từ đã có trong bài')).toBeInTheDocument()
    expect((calls.find((c) => c.method === 'POST')?.body as { source: string }).source).toBe('MANUAL')
  })
})
