import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../../lib/api'
import { auth, json, renderAt } from '../../../testUtils'
import { uploadVideo } from './questions/upload'

vi.mock('./questions/upload', () => ({ uploadAudio: vi.fn(), uploadImage: vi.fn(), uploadVideo: vi.fn() }))

const course = { id: 7, slug: 'c', title: 'C', description: null, targetType: null, status: 'DRAFT', sequential: true, ielts: false, lessonCompletePercent: 80, examUnlockPercent: 70, examRetakes: 2, deliveryMode: 'SELF_PACED' }
const lesson = { id: 30, topicId: 20, type: 'CONNECTIVE_VIDEO', title: 'Bài 1', durationSec: 120, sortOrder: 0, status: 'DRAFT', videoSource: 'YOUTUBE', videoRef: 'abc', videoUrl: null, subtitles: [] }
type L = typeof lesson | (Omit<typeof lesson, 'videoUrl'> & { videoUrl: string | null })
const tree = (l: L) => ({
  course,
  levels: [{ level: { id: 10, courseId: 7, code: 'A1', title: 'A1', sortOrder: 0, status: 'DRAFT' }, topics: [{ topic: { id: 20, levelId: 10, title: 'T', sortOrder: 0, isFreeTrial: false, skill: 'LISTENING', status: 'DRAFT' }, lessons: [l] }] }],
})

function mock(state: { lesson: L }, handlers: Record<string, (body: unknown) => Response> = {}) {
  const calls: { url: string; method: string; body: unknown }[] = []
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET'
    if (url === '/api/auth/refresh') return json(200, auth)
    if (url === '/api/me/permissions') return json(200, { permissions: ['cms.content'] })
    const body = init?.body instanceof FormData ? init.body : init?.body ? JSON.parse(String(init.body)) : undefined
    calls.push({ url, method, body })
    if (url === '/api/admin/cms/courses/7') return json(200, tree(state.lesson)) // cây thật: videoUrl luôn null
    if (url === '/api/admin/cms/lessons/30/glossary') return json(200, [])
    return (handlers[`${method} ${url}`] ?? (() => json(404)))(body)
  })
  return calls
}
const open = () => renderAt('/quan-tri/cms/khoa-hoc/7/bai-giang/30')

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

describe('tester: CMS video bài giảng khớp backend', () => {
  test('sau khi tải video lên, biên tập viên xem thử được (cây CMS trả videoUrl null như backend)', async () => {
    vi.mocked(uploadVideo).mockResolvedValue({ id: 77 } as never)
    const state = { lesson: lesson as L }
    mock(state, {
      'PATCH /api/admin/cms/lessons/30': () => {
        state.lesson = { ...lesson, videoSource: 'STORAGE', videoRef: 'media/lesson-video/k', videoUrl: null }
        return json(200, { ...state.lesson, videoUrl: 'http://s/v.mp4' })
      },
    })
    open()
    await userEvent.click(await screen.findByLabelText('Tải video lên'))
    await userEvent.upload(screen.getByLabelText('Chọn tệp video (mp4)'), new File(['x'], 'v.mp4', { type: 'video/mp4' }))
    expect(await screen.findByText(/Video đã tải lên/)).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Xem thử' })).toBeInTheDocument()
  })

  test('đổi từ video tải lên sang YouTube gửi videoSource YOUTUBE kèm videoRef', async () => {
    const calls = mock({ lesson: { ...lesson, videoSource: 'STORAGE', videoRef: 'media/lesson-video/k' } }, { 'PATCH /api/admin/cms/lessons/30': () => json(200, lesson) })
    open()
    await userEvent.click(await screen.findByLabelText('YouTube'))
    await userEvent.type(screen.getByLabelText('Video (mã hoặc URL YouTube)'), 'dQw4w9WgXcQ')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu video' }))
    await waitFor(() => expect(calls.find((c) => c.method === 'PATCH')?.body).toMatchObject({ videoSource: 'YOUTUBE', videoRef: 'dQw4w9WgXcQ' }))
  })

  test('lỗi phụ đề đúng định dạng backend: validation_failed details.file và import_file_too_large', async () => {
    mock({ lesson }, {
      'PUT /api/admin/cms/lessons/30/subtitles/en': () => json(400, { error: { code: 'validation_failed', message: 'Dữ liệu không hợp lệ', details: { file: 'Dòng 3: không có dòng thời gian hợp lệ' } } }),
      'PUT /api/admin/cms/lessons/30/subtitles/vi': () => json(400, { error: { code: 'import_file_too_large', message: 'File quá lớn', details: {} } }),
    })
    open()
    const f = new File(['x'], 'a.srt')
    await userEvent.upload(await screen.findByLabelText('Tiếng Anh'), f)
    expect(await screen.findByText('Dòng 3: không có dòng thời gian hợp lệ')).toBeInTheDocument()
    await userEvent.upload(screen.getByLabelText('Tiếng Việt'), f)
    expect(await screen.findByText('File quá lớn')).toBeInTheDocument()
  })
})
