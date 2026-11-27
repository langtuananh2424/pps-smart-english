import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import type { Bank, Question } from '../practice/types'

const wq = (id: number, task: string): Question => ({ id, type: 'WRITING', prompt: `Đề ${id}`, payload: { task, requiredWords: 50 } })
const sq = (id: number, part: number, type = 'SPEAKING'): Question => ({ id, type, prompt: `Nói ${id}`, payload: { part, prompt: `Nói ${id}` } })
const bank = (skill: Bank['skill'], questions: Question[], extra: Partial<Bank> = {}): Bank => ({
  id: 7, title: 'IELTS', lessonId: null, isTrial: false, mode: 'PRACTICE', timeLimitSec: 600, skill, ielts: true, questions, ...extra,
})

function wire(b: Bank) {
  return mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/banks/7': () => json(200, b),
    '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [{ id: 9, bankId: 7, startedAt: 'x' }] }),
    '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false, remainingSec: 600, mode: 'PRACTICE' }),
    '/api/attempts/9/hints': () => json(200, []),
    '/api/attempts/9/ai-sessions': () => json(200, { data: [] }),
    '/api/attempts/9/draft': () => json(200, { attemptId: 9, paused: false }),
    '/api/speaking/questions/21': () => json(200, { id: 21, part: 2, prompt: 'x', cueCard: ['who'], maxSeconds: 60 }),
    '/api/speaking/questions/22': () => json(200, { id: 22, part: 1, prompt: 'x', cueCard: [], maxSeconds: 60 }),
    '/api/speaking/questions/23': () => json(200, { id: 23, part: 1, prompt: 'x', cueCard: [], maxSeconds: 60 }),
  })
}

describe('khung IELTS Viết/Nói', () => {
  beforeEach(() => {
    setAccessToken('t')
    sessionStorage.clear()
  })

  test('bộ IELTS Writing vào khung IELTS, không vào Player; đếm từ và tab Task', async () => {
    wire(bank('WRITING', [wq(1, 'Task one chart'), wq(2, 'Task two essay')]))
    const router = renderAt('/bai-tap/7')
    expect(await screen.findByRole('textbox', { name: 'Nội dung bài viết' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/ielts/7')
    expect(screen.queryByRole('navigation', { name: 'Điều hướng câu hỏi' })).not.toBeInTheDocument()
    expect(screen.getByText('0/50 từ')).toBeInTheDocument()
    await userEvent.type(screen.getByRole('textbox', { name: 'Nội dung bài viết' }), 'one two three')
    expect(screen.getByText('3/50 từ')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Task 2' }))
    expect(screen.getByText('Task two essay')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Đề bài' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Bài viết' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nộp bài' })).toBeInTheDocument()
  })

  test('bộ Writing không có cờ IELTS đi Player thường', async () => {
    wire(bank('WRITING', [wq(1, 'a')], { ielts: false }))
    const router = renderAt('/bai-tap/7')
    expect(await screen.findByRole('navigation', { name: 'Điều hướng câu hỏi' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/bai-tap/7')
  })

  test('Speaking: tab theo part, Part 2 có nhãn chuẩn bị, nút Kết thúc', async () => {
    wire(bank('SPEAKING', [sq(22, 1), sq(23, 1), sq(21, 2)]))
    renderAt('/ielts/7')
    expect(await screen.findByRole('tab', { name: 'Part 1' })).toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: 'Part 3' })).not.toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(2)
    expect(screen.queryByText('Chuẩn bị 1 phút · Nói 1–2 phút')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Part 2' }))
    expect(screen.getByText('Chuẩn bị 1 phút · Nói 1–2 phút')).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Kết thúc' })).toBeInTheDocument()
  })
})

describe('LISTEN_RECORD', () => {
  test('chỉ có chọn tệp, không ghi trực tiếp; nút Nộp bản ghi', async () => {
    setAccessToken('t')
    // Giả lập trình duyệt ghi được: Nói thường có nút ghi âm, LISTEN_RECORD thì không.
    vi.stubGlobal('MediaRecorder', class {})
    Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia: vi.fn() }, configurable: true })
    wire(bank('SPEAKING', [sq(22, 1, 'LISTEN_RECORD'), sq(23, 1)]))
    renderAt('/ielts/7')
    const cards = await screen.findAllByRole('article')
    expect(await screen.findAllByText('Chọn tệp audio')).toHaveLength(2)
    expect(cards[0]).not.toHaveTextContent('Bắt đầu ghi âm')
    expect(await screen.findAllByRole('button', { name: 'Bắt đầu ghi âm' })).toHaveLength(1)
    vi.unstubAllGlobals()
  })
})
