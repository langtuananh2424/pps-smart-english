import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import type { Bank } from '../practice/types'

// Tester D-150/D-151: Nghe ở lượt Mô phỏng thi trên bộ FREE, khôi phục trạng thái khi tải lại, lỗi mạng.
const mcq = (id: number, groupId: number) => ({
  id, type: 'CONNECTIVE_MCQ', prompt: `Câu ${id}?`, payload: {}, groupId,
  choices: [{ id: id * 10 + 1, label: 'A', content: `Đúng ${id}` }, { id: id * 10 + 2, label: 'B', content: `Sai ${id}` }],
})
const listening = (): Bank => ({
  id: 7, title: 'Listening 1', lessonId: null, isTrial: false, mode: 'PRACTICE', timeLimitSec: 600, skill: 'LISTENING',
  playbackMode: 'FREE', playLimit: null,
  questions: [mcq(1, 1), mcq(2, 1)],
  groups: [{ id: 1, title: 'Part 1', instruction: null, passageHtml: null, sortOrder: 0, hasAudio: true, audioDurationMs: 60_000 }],
})
const reading = (): Bank => ({ ...listening(), skill: 'READING', groups: [{ id: 1, title: 'Passage 1', instruction: null, passageHtml: '<p>Bài đọc</p>', sortOrder: 0, hasAudio: false, audioDurationMs: null }] })

const fail = () => { throw new TypeError('Failed to fetch') }

function wire(mode: 'PRACTICE' | 'EXAM', progress: Record<string, unknown>, over: Record<string, (b: unknown) => Response> = {}, b: Bank = reading()) {
  return mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/banks/7': () => json(200, b),
    '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [{ id: 9, bankId: 7, startedAt: 'x' }] }),
    '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false, remainingSec: 600, mode, ...progress }),
    '/api/attempts/9/hints': () => json(200, []),
    '/api/attempts/9/draft': () => json(200, { attemptId: 9, paused: false, mode }),
    ...over,
  })
}

let audios: HTMLMediaElement[] = []
beforeEach(() => {
  setAccessToken('t')
  audios = []
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(function (this: HTMLMediaElement) {
    audios.push(this)
    this.dispatchEvent(new Event('play'))
    return Promise.resolve()
  })
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe('IELTS Listening, lượt Mô phỏng thi trên bộ playbackMode FREE (D-151 mục 7)', () => {
  const examGrant = () => json(200, { playbackMode: 'EXAM', playNo: 1, playLimit: 1, remaining: 0, url: 'https://cdn/a.mp3', expiresAt: 'x' })

  test('không có thanh tua audio', async () => {
    wire('EXAM', {}, { '/api/attempts/9/groups/1/play': examGrant }, listening())
    renderAt('/ielts/7')
    await screen.findByRole('article', { name: 'Câu 1' })
    await screen.findByRole('region', { name: 'Audio' })
    expect(screen.queryByLabelText('Tua audio')).not.toBeInTheDocument()
  })

  test('phát hết thì báo máy chủ (complete) để máy chủ đếm đúng lượt nghe', async () => {
    const calls = wire('EXAM', {}, {
      '/api/attempts/9/groups/1/play': examGrant,
      '/api/attempts/9/groups/1/play/complete': () => json(200, { playNo: 1, completed: true, playLimit: 1, remaining: 0 }),
    }, listening())
    renderAt('/ielts/7')
    await screen.findByRole('article', { name: 'Câu 1' })
    const region = await screen.findByRole('region', { name: 'Audio' })
    const btn = within(region).getAllByRole('button')[0]
    if (btn.textContent?.includes('Phát')) await userEvent.click(btn) // FREE (sai) thì chờ bấm; EXAM thì tự phát
    await waitFor(() => expect(audios.length).toBeGreaterThan(0))
    await act(async () => void audios.at(-1)!.dispatchEvent(new Event('ended')))
    await waitFor(() => expect(calls).toContain('/api/attempts/9/groups/1/play/complete'))
  })
})

describe('khôi phục khi tải lại và lỗi mạng', () => {
  test('lượt Mô phỏng thi mở lại: không Kiểm tra, không Cam, không Tạm dừng, không gọi gợi ý', async () => {
    const calls = wire('EXAM', { answers: [{ questionId: 1, answer: { choiceId: 11 } }] })
    renderAt('/ielts/7')
    expect(await screen.findByRole('radio', { name: /Đúng 1/ })).toHaveAttribute('aria-checked', 'true')
    expect(screen.queryByRole('button', { name: 'Kiểm tra' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Hỏi Cam' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tạm dừng' })).not.toBeInTheDocument()
    expect(calls).not.toContain('/api/attempts/9/hints')
    expect(calls).not.toContain('/api/banks/7/attempts')
  })

  test('câu đã khóa (hết lượt thử) giữ khóa sau khi tải lại', async () => {
    wire('PRACTICE', {
      answers: [{ questionId: 1, answer: { choiceId: 12 } }],
      retry: { maxRetries: 1, checks: [{ questionId: 1, correct: false, checksLeft: 0 }, { questionId: 2, correct: true, checksLeft: 2 }] },
    })
    renderAt('/ielts/7')
    const q1 = await screen.findByRole('article', { name: 'Câu 1' })
    expect(within(q1).getByText('Chưa đúng. Xem đáp án ở màn kết quả')).toBeInTheDocument()
    expect(within(q1).queryByRole('button', { name: 'Kiểm tra' })).not.toBeInTheDocument()
    expect(within(q1).getByRole('radio', { name: /Đúng 1/ })).toBeDisabled()
    const q2 = screen.getByRole('article', { name: 'Câu 2' })
    expect(within(q2).getByText('Đúng rồi!')).toBeInTheDocument()
    expect(within(q2).getByRole('radio', { name: /Sai 2/ })).toBeDisabled()
  })

  test('mất mạng khi Kiểm tra: báo lỗi, câu không bị khóa, thử lại được', async () => {
    wire('PRACTICE', { retry: { maxRetries: 1, checks: [] } }, { '/api/attempts/9/questions/1/check': fail })
    renderAt('/ielts/7')
    const q1 = await screen.findByRole('article', { name: 'Câu 1' })
    await userEvent.click(within(q1).getByRole('radio', { name: /Sai 1/ }))
    await userEvent.click(within(q1).getByRole('button', { name: 'Kiểm tra' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Không kiểm tra được, kiểm tra kết nối rồi thử lại.')
    expect(within(q1).getByRole('button', { name: 'Kiểm tra' })).toBeEnabled()
  })

  test('mất mạng khi tải tiến độ: có nút Thử lại', async () => {
    wire('PRACTICE', {}, { '/api/attempts/9/progress': fail })
    renderAt('/ielts/7')
    expect(await screen.findByRole('button', { name: /Thử lại/ })).toBeInTheDocument()
  })

  test('mất mạng khi Bắt đầu: báo lỗi tại màn bắt đầu, bấm lại được', async () => {
    wire('PRACTICE', {}, { '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [] }), '/api/banks/7/attempts': fail })
    renderAt('/ielts/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bắt đầu' })).toBeEnabled()
  })
})

describe('Luyện tập trên bộ Listening FREE vẫn tự do', () => {
  test('có thanh tua, không gọi complete', async () => {
    const calls = wire('PRACTICE', {}, { '/api/attempts/9/groups/1/play': () => json(200, { playbackMode: 'FREE', url: 'u', expiresAt: 'x' }) }, listening())
    renderAt('/ielts/7')
    await screen.findByRole('article', { name: 'Câu 1' })
    expect(await screen.findByLabelText('Tua audio')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Phát/ }))
    await waitFor(() => expect(audios.length).toBeGreaterThan(0))
    await act(async () => void audios.at(-1)!.dispatchEvent(new Event('ended')))
    expect(calls).not.toContain('/api/attempts/9/groups/1/play/complete')
  })
})
