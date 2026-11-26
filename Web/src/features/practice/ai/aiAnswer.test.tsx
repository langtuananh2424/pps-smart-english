import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../../lib/api'
import { auth, json, mockApi, renderAt } from '../../../testUtils'
import type { Bank } from '../types'

// Câu Viết/Nói làm ngay trong khung Player (Figma 13A), gửi chấm từ thanh dưới.
const uploads = vi.hoisted(() => ({ run: vi.fn() }))
vi.mock('../../speaking/api', async (orig) => ({ ...(await orig<object>()), uploadAudioWithProgress: uploads.run }))
vi.mock('../../speaking/wav', async (orig) => ({ ...(await orig<object>()), toWav16k: vi.fn(async () => ({ wav: new Blob(['w']), seconds: 8 })) }))
import * as wav from '../../speaking/wav'

const bank: Bank = {
  id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null,
  questions: [
    { id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Chọn', payload: {}, choices: [{ id: 5, label: 'A', content: 'So' }] },
    { id: 2, type: 'WRITING', prompt: 'Viết bài', payload: {} },
    { id: 3, type: 'SPEAKING', prompt: 'Nói đi', payload: {} },
  ],
}
const quota = (remaining: number) => ({ pool: 0, courses: [{ courseId: 1, courseTitle: 'K', period: 'WEEK', quota: 5, used: 5 - remaining, remaining, resetsAt: '2026-11-30T00:00:00+07:00' }] })
const noQuota = () => json(402, { error: { code: 'quota_exhausted', message: 'Hết lượt', details: { products: [] } } })

describe('câu Viết/Nói trong khung làm bài', () => {
  beforeEach(() => {
    setAccessToken('t')
    sessionStorage.clear()
    uploads.run.mockReset()
    URL.createObjectURL = vi.fn(() => 'blob:x')
    URL.revokeObjectURL = vi.fn()
  })
  const posts: { url: string; body: unknown; key?: string | null }[] = []
  const wire = (over: Record<string, (b: unknown, i?: RequestInit) => Response> = {}, aiSessions: unknown[] = []) => {
    posts.length = 0
    const rec = (url: string, res: (b: unknown) => Response) => (b: unknown, i?: RequestInit) => (posts.push({ url, body: b, key: new Headers(i?.headers).get('Idempotency-Key') }), res(b))
    return mockApi({
      '/api/auth/refresh': () => json(200, auth),
      '/api/banks/7': () => json(200, bank),
      '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [] }),
      '/api/banks/7/attempts': () => json(201, { id: 9, bankId: 7, startedAt: 'x', timeLimitSec: null }),
      '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false }),
      '/api/attempts/9/ai-sessions': () => json(200, { data: aiSessions }),
      '/api/me/quota': () => json(200, quota(3)),
      '/api/writing/sessions': rec('create', () => json(201, { sessionId: posts.length + 100, question: { id: 2, prompt: 'x' } })),
      '/api/writing/sessions/101/submit': rec('submit', () => json(202, { jobId: 5, status: 'QUEUED', quotaRemaining: 2 })),
      '/api/writing/sessions/102/submit': rec('submit', () => json(202, { jobId: 6, status: 'QUEUED', quotaRemaining: 1 })),
      '/api/speaking/questions/3': () => json(200, { id: 3, part: 1, prompt: 'Nói đi', cueCard: [], maxSeconds: 180 }),
      '/api/speaking/submissions': rec('speak', () => json(201, { submissionId: 31, url: 'http://minio/u', fields: {}, maxBytes: 9, contentType: 'audio/wav', maxSeconds: 180, expiresAt: 'x' })),
      '/api/speaking/submissions/31/complete': rec('complete', () => json(200, { submissionId: 31, status: 'GRADING', jobId: 8, quotaRemaining: 2 })),
      ...over,
    })
  }
  const go = async (n: number) => {
    renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await userEvent.click(await screen.findByRole('button', { name: new RegExp(`^Câu ${n}`) }))
  }
  const write = async (text = 'Online learning is good.') => userEvent.type(await screen.findByLabelText('Nội dung bài viết'), text)
  const sendBtn = () => screen.getByRole('button', { name: 'Gửi chấm' })

  describe('Viết', () => {
    test('Gửi chấm tạo phiên gắn lượt rồi nộp, sau đó khóa bài, hiện dải xanh và mốc câu xanh', async () => {
      wire()
      await go(2)
      expect(sendBtn()).toBeDisabled()
      await write()
      expect(screen.getByText('4 từ')).toBeInTheDocument()
      await userEvent.click(sendBtn())
      await screen.findByText(/Đã gửi chấm · Kết quả hiện sau khi nộp bài/)
      expect(posts.map((p) => p.url)).toEqual(['create', 'submit'])
      expect(posts[0].body).toEqual({ questionId: 2, attemptId: 9 })
      expect(posts[1].body).toEqual({ finalText: 'Online learning is good.' })
      expect(posts[1].key).toBeTruthy()
      expect(screen.getByLabelText('Nội dung bài viết')).toHaveAttribute('readonly')
      expect(screen.queryByRole('button', { name: 'Gửi chấm' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Câu 2, đã làm' })).toBeInTheDocument()
    })

    test('Viết lại hỏi xác nhận rồi mở lại bài để sửa, gửi lại tạo phiên mới', async () => {
      wire({}, [{ questionId: 2, kind: 'WRITING', id: 90, status: 'GRADED' }])
      sessionStorage.setItem('ai:writing:9:2', 'Old text')
      await go(2)
      await userEvent.click(await screen.findByRole('button', { name: 'Viết lại' }))
      const dlg = screen.getByRole('alertdialog')
      expect(dlg).toHaveTextContent('Viết lại câu này?')
      await userEvent.click(within(dlg).getByRole('button', { name: 'Viết lại' }))
      const box = screen.getByLabelText('Nội dung bài viết')
      expect(box).not.toHaveAttribute('readonly')
      expect(box).toHaveValue('Old text') // giữ bài cũ để sửa
      await userEvent.type(box, ' more')
      await userEvent.click(sendBtn())
      await waitFor(() => expect(posts.map((p) => p.url)).toEqual(['create', 'submit']))
    })

    test('hết lượt (402): hiện thẻ hết lượt, giữ bài đã viết, khóa Gửi chấm', async () => {
      wire({ '/api/writing/sessions/101/submit': () => noQuota() })
      await go(2)
      await write()
      await userEvent.click(sendBtn())
      const card = await screen.findByRole('alert', { name: 'Hết lượt AI' })
      expect(card).toHaveTextContent('0 lượt')
      expect(card).toHaveTextContent(/Thứ Hai, 30\/11\/2026/)
      expect(within(card).getByRole('link', { name: 'Mua thêm lượt' })).toHaveAttribute('href', '/don-hang')
      expect(screen.getByLabelText('Nội dung bài viết')).toHaveValue('Online learning is good.')
      expect(sendBtn()).toBeDisabled()
    })

    test('ví đã về 0 từ trước: chỉ hiện thẻ hết lượt; Bỏ qua câu này sang câu kế', async () => {
      wire({ '/api/me/quota': () => json(200, quota(0)) })
      await go(2)
      await screen.findByRole('alert', { name: 'Hết lượt AI' })
      expect(screen.queryByLabelText('Nội dung bài viết')).not.toBeInTheDocument()
      await userEvent.click(screen.getByRole('button', { name: 'Bỏ qua câu này' }))
      expect(await screen.findByRole('article', { name: 'Câu 3' })).toBeInTheDocument()
    })

    test('bản nộp NOT_SUBMITTED coi như chưa gửi: không khóa, không báo đang chấm, mốc chưa xanh', async () => {
      wire({}, [{ questionId: 2, kind: 'WRITING', id: 101, status: 'NOT_SUBMITTED' }])
      await go(2)
      await write()
      expect(screen.getByLabelText('Nội dung bài viết')).not.toHaveAttribute('readonly')
      expect(screen.queryByText(/Đã gửi chấm/)).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Câu 2' })).toBeInTheDocument()
      await userEvent.click(sendBtn())
      await screen.findByText(/Đã gửi chấm/)
      expect(posts.map((p) => p.url)).toEqual(['submit']) // dùng lại phiên đã mở, không tạo thêm
    })
  })

  describe('Nói', () => {
    const pick = async (name = 'ban-ghi.m4a') => userEvent.upload(await screen.findByLabelText('Chọn tệp audio'), new File(['x'], name, { type: 'audio/mp4' }))

    test('chọn tệp rồi Gửi chấm: tải lên có tiến độ, xác nhận, khóa và hiện Nói lại', async () => {
      uploads.run.mockImplementation(async (_p, _w, onProgress: (n: number) => void) => onProgress(100))
      wire()
      await go(3)
      expect(await screen.findByText(/MP3, M4A, WAV · tối đa 3 phút/)).toBeInTheDocument()
      await pick()
      expect(await screen.findByText('ban-ghi.m4a')).toBeInTheDocument()
      await userEvent.click(sendBtn())
      await screen.findByText(/Đã gửi chấm/)
      expect(posts.map((p) => p.url)).toEqual(['speak', 'complete'])
      expect(posts[0].body).toEqual({ questionId: 3, attemptId: 9 })
      expect(screen.getByRole('button', { name: 'Nói lại' })).toBeInTheDocument()
      await userEvent.click(screen.getByRole('button', { name: 'Nói lại' }))
      expect(screen.getByRole('alertdialog')).toHaveTextContent('Nói lại câu này?')
    })

    test('Hủy khi đang tải lên: dừng, giữ tệp đã chọn, không xác nhận, không báo lỗi', async () => {
      uploads.run.mockImplementation((_p, _w, onProgress: (n: number) => void, signal: AbortSignal) => new Promise((_, reject) => {
        onProgress(58)
        signal.addEventListener('abort', () => reject(new DOMException('x', 'AbortError')))
      }))
      wire()
      await go(3)
      await pick()
      await userEvent.click(await screen.findByRole('button', { name: 'Gửi chấm' }))
      expect(await screen.findByRole('progressbar', { name: 'Tiến độ tải lên' })).toHaveAttribute('aria-valuenow', '58')
      await userEvent.click(screen.getByRole('button', { name: 'Hủy' }))
      expect(await screen.findByText('ban-ghi.m4a')).toBeInTheDocument()
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(posts.map((p) => p.url)).toEqual(['speak'])
      expect(sendBtn()).toBeEnabled()
    })

    test('tệp quá dài hoặc không đọc được: báo lỗi, không có bản ghi để gửi', async () => {
      wire()
      await go(3)
      vi.mocked(wav.toWav16k).mockRejectedValueOnce(new wav.AudioError('too_long'))
      await pick('dai.mp3')
      expect(await screen.findByRole('alert')).toHaveTextContent('dài hơn 180 giây')
      expect(sendBtn()).toBeDisabled()
      vi.mocked(wav.toWav16k).mockRejectedValueOnce(new wav.AudioError('undecodable'))
      await pick('hong.txt.mp3')
      expect(await screen.findByRole('alert')).toHaveTextContent('Không đọc được tệp')
    })

    test('hết lượt khi gửi: thẻ hết lượt, vẫn giữ tệp đã chọn', async () => {
      wire({ '/api/speaking/submissions': () => noQuota() })
      await go(3)
      await pick()
      await userEvent.click(await screen.findByRole('button', { name: 'Gửi chấm' }))
      await screen.findByRole('alert', { name: 'Hết lượt AI' })
      expect(screen.getByText('ban-ghi.m4a')).toBeInTheDocument()
      expect(sendBtn()).toBeDisabled()
    })

    test('bản nộp AWAITING_UPLOAD (NOT_SUBMITTED) là chưa gửi: không khóa, hiện chọn tệp', async () => {
      wire({}, [{ questionId: 3, kind: 'SPEAKING', id: 30, status: 'NOT_SUBMITTED' }])
      await go(3)
      expect(await screen.findByLabelText('Chọn tệp audio')).toBeInTheDocument()
      expect(screen.queryByText(/Đã gửi chấm|Đang chấm/)).not.toBeInTheDocument()
    })
  })

  describe('hộp Nộp bài', () => {
    const open = async () => userEvent.click(screen.getByRole('button', { name: 'Nộp bài' }))
    test('liệt kê câu chưa làm, dòng tính 0 điểm; quay lại câu khi bấm số', async () => {
      wire()
      await go(3)
      await open()
      const dlg = await screen.findByRole('alertdialog')
      expect(dlg).toHaveTextContent('Còn 3 câu chưa làm')
      expect(dlg).toHaveTextContent('Trong đó 2 câu Viết/Nói sẽ tính 0 điểm.')
      expect(within(dlg).queryByRole('status')).not.toBeInTheDocument() // không có câu đang chấm
      await userEvent.click(within(dlg).getByRole('button', { name: 'Quay lại câu 1' }))
      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
      expect(screen.getByRole('article', { name: 'Câu 1' })).toBeInTheDocument()
    })

    test('câu đang chấm: dải vàng; không còn câu Viết/Nói chưa làm thì không có dòng 0 điểm', async () => {
      wire({}, [{ questionId: 2, kind: 'WRITING', id: 31, status: 'GRADING' }, { questionId: 3, kind: 'SPEAKING', id: 32, status: 'GRADING' }])
      await go(3)
      await open()
      const dlg = await screen.findByRole('alertdialog')
      expect(dlg).toHaveTextContent('Còn 1 câu chưa làm')
      expect(within(dlg).getByRole('status')).toHaveTextContent('2 câu Viết/Nói đang chấm')
      expect(dlg).not.toHaveTextContent('tính 0 điểm')
    })
  })
})
