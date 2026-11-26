import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import type { Bank } from '../practice/types'
import { buildParts, isIeltsBank, navState } from './parts'

const mcq = (id: number, prompt: string, groupId: number) => ({
  id, type: 'CONNECTIVE_MCQ', prompt, payload: {}, groupId,
  choices: [{ id: id * 10 + 1, label: 'A', content: `Đúng ${id}` }, { id: id * 10 + 2, label: 'B', content: `Sai ${id}` }],
})
const bank = (extra: Partial<Bank> = {}): Bank => ({
  id: 7, title: 'Reading 1', lessonId: null, isTrial: false, mode: 'PRACTICE', timeLimitSec: 600, skill: 'READING',
  questions: [mcq(1, 'Câu một?', 1), mcq(2, 'Câu hai?', 1), mcq(3, 'Câu ba?', 2)],
  groups: [
    { id: 2, title: 'Passage Hai', instruction: 'Chọn B', passageHtml: '<p>Nội dung hai</p>', sortOrder: 1, hasAudio: false, audioDurationMs: null },
    { id: 1, title: 'Passage Một', instruction: 'Chọn A', passageHtml: '<p>Nội dung một</p>', sortOrder: 0, hasAudio: false, audioDurationMs: null },
  ],
  ...extra,
})
const result = {
  id: 9, bankId: 7, score: 2, maxScore: 3,
  results: [
    { questionId: 1, isCorrect: true, correctAnswer: { choiceId: 11 }, explanation: 'Vì đoạn A.' },
    { questionId: 2, isCorrect: false, correctAnswer: { choiceId: 21 }, explanation: 'Xem đoạn B.' },
    { questionId: 3, isCorrect: true, correctAnswer: { choiceId: 31 } },
  ],
  bands: { skills: { READING: { correct: 2, total: 3, band: 6.5 } }, overall: 6.5, gradingPending: false },
}
const hints = [{ questionId: 1, hints: [{ id: 1, content: 'Tìm từ khóa', detail: 'Chi tiết dài' }] }]

function wire(mode: 'PRACTICE' | 'EXAM', over: Record<string, (b: unknown) => Response> = {}, b = bank()) {
  return mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/banks/7': () => json(200, b),
    '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [] }),
    '/api/banks/7/attempts': () => json(201, { id: 9, bankId: 7, startedAt: 'x', timeLimitSec: 600, mode }),
    '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false, remainingSec: 600, mode }),
    '/api/attempts/9/hints': () => json(200, hints),
    '/api/attempts/9/draft': () => json(200, { attemptId: 9, paused: false, mode }),
    '/api/attempts/9/submit': () => json(200, { id: 9, bankId: 7 }),
    '/api/attempts/9': () => json(200, result),
    ...over,
  })
}

describe('parts', () => {
  test('chia theo nhóm đúng thứ tự sortOrder, câu lẻ thành phần cuối', () => {
    const { parts, ordered } = buildParts(bank({ questions: [...bank().questions, { id: 4, type: 'CONNECTIVE_MCQ', prompt: 'Lẻ', payload: {} }] }))
    expect(parts.map((p) => [p.key, p.first, p.questions.length])).toEqual([['g1', 0, 2], ['g2', 2, 1], ['rest', 3, 1]])
    expect(ordered.map((q) => q.id)).toEqual([1, 2, 3, 4])
  })
  test('khung IELTS chỉ cho bộ PRACTICE có skill Reading/Listening', () => {
    expect(isIeltsBank({ skill: 'READING', mode: 'PRACTICE' })).toBe(true)
    expect(isIeltsBank({ skill: 'LISTENING', mode: 'PRACTICE' })).toBe(true)
    expect(isIeltsBank({ skill: 'READING', mode: 'EXAM' })).toBe(false) // bài thi khóa
    expect(isIeltsBank({ skill: 'WRITING', mode: 'PRACTICE' })).toBe(false)
    expect(isIeltsBank({ mode: 'PRACTICE' })).toBe(false)
  })
  test('đánh dấu xem lại ưu tiên hơn đã trả lời', () => {
    expect(navState(true, true)).toBe('flagged')
    expect(navState(true, false)).toBe('answered')
    expect(navState(false, false)).toBe('empty')
  })
})

describe('khung thi IELTS', () => {
  beforeEach(() => setAccessToken('t'))

  test('PracticePage chuyển bộ IELTS sang /ielts và Bắt đầu gửi chế độ đã chọn', async () => {
    const calls = wire('EXAM')
    let body: unknown
    mockApiBody((b) => { body = b })
    const router = renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('radio', { name: 'Mô phỏng thi' }))
    await userEvent.click(screen.getByRole('button', { name: 'Bắt đầu' }))
    expect(await screen.findByRole('article', { name: 'Câu 1' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/ielts/7')
    expect(body).toEqual({ mode: 'EXAM' })
    expect(calls).toContain('/api/banks/7/attempts')
  })

  test('Luyện tập: tạm dừng, Cam gợi ý theo câu, popup gợi ý dài, thu gọn', async () => {
    wire('PRACTICE')
    renderAt('/ielts/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    expect(await screen.findByText('Tìm từ khóa')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tạm dừng' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Xem chi tiết' }))
    expect(within(screen.getByRole('alertdialog')).getByText('Chi tiết dài')).toBeInTheDocument()
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Đóng' }))
    await userEvent.click(screen.getByRole('button', { name: 'Thu gọn Cam' }))
    expect(screen.queryByText('Tìm từ khóa')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Câu 3' })) // câu 3 không có gợi ý
    expect(screen.queryByRole('complementary', { name: 'Cam gợi ý' })).not.toBeInTheDocument()
  })

  test('Mô phỏng thi: không tạm dừng, không gọi gợi ý', async () => {
    const calls = wire('EXAM')
    renderAt('/ielts/7')
    await userEvent.click(await screen.findByRole('radio', { name: 'Mô phỏng thi' }))
    await userEvent.click(screen.getByRole('button', { name: 'Bắt đầu' }))
    await screen.findByRole('article', { name: 'Câu 1' })
    expect(screen.queryByRole('button', { name: 'Tạm dừng' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Hỏi Cam' })).not.toBeInTheDocument()
    expect(screen.getByRole('timer')).toHaveTextContent('10:00')
    expect(calls).not.toContain('/api/attempts/9/hints')
  })

  test('Luyện tập có thử lại: kiểm tra sai mở Cam với gợi ý câu, Mô phỏng thi không có nút Kiểm tra (D-150)', async () => {
    const calls: unknown[] = []
    wire('PRACTICE', {
      '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false, remainingSec: 600, mode: 'PRACTICE', retry: { maxRetries: 1, checks: [] } }),
      '/api/attempts/9/questions/1/check': (b) => { calls.push(b); return json(200, { questionId: 1, correct: false, checksLeft: 1 }) },
    })
    renderAt('/ielts/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    const q1 = await screen.findByRole('article', { name: 'Câu 1' })
    await userEvent.click(within(q1).getByRole('radio', { name: /Sai 1/ }))
    await userEvent.click(within(q1).getByRole('button', { name: 'Kiểm tra' }))
    expect(calls).toEqual([{ answer: { choiceId: 12 } }])
    expect(await screen.findByText('Cần sửa, thử lại nhé')).toBeInTheDocument()
    expect(screen.getByText('Tìm từ khóa')).toBeInTheDocument()
  })

  test('Mô phỏng thi không có nút Kiểm tra dù bộ là PRACTICE', async () => {
    wire('EXAM')
    renderAt('/ielts/7')
    await userEvent.click(await screen.findByRole('radio', { name: 'Mô phỏng thi' }))
    await userEvent.click(screen.getByRole('button', { name: 'Bắt đầu' }))
    await screen.findByRole('article', { name: 'Câu 1' })
    expect(screen.queryByRole('button', { name: 'Kiểm tra' })).not.toBeInTheDocument()
  })

  test('điều hướng câu đổi phần, đánh dấu, xem lại, xác nhận rồi nộp và xem band', async () => {
    const calls = wire('PRACTICE')
    renderAt('/ielts/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    expect(await screen.findByText('Nội dung một')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('radio', { name: /Đúng 1/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Câu 3' }))
    expect(await screen.findByText('Nội dung hai')).toBeInTheDocument() // sang Passage 2
    expect(screen.getByRole('article', { name: 'Câu 3' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Đánh dấu xem lại' }))
    expect(screen.getByRole('button', { name: 'Câu đã đánh dấu' })).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(screen.getByRole('button', { name: 'Nộp bài' }))
    expect(await screen.findByRole('heading', { name: 'Xem lại trước khi nộp' })).toBeInTheDocument()
    expect(screen.getByText('1 Đã trả lời · 1 Xem lại · 1 Chưa trả lời')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Câu 2' })) // quay lại bài làm ở câu 2
    expect(await screen.findByRole('article', { name: 'Câu 2' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Nộp bài' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Xác nhận nộp' }))
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Làm tiếp' }))
    expect(calls).not.toContain('/api/attempts/9/submit') // chưa xác nhận thì chưa nộp
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận nộp' }))
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Xác nhận' }))

    expect(await screen.findByText('2/3 câu đúng')).toBeInTheDocument()
    expect(screen.getByLabelText('Band')).toHaveTextContent('6.5')
    expect(screen.getByText('Vì đoạn A.')).toBeInTheDocument()
    expect(screen.getByText('Đáp án: A. Đúng 1')).toBeInTheDocument()
  })

  test('có lượt dở thì làm tiếp với nháp, không tạo lượt mới', async () => {
    const calls = wire('PRACTICE', {
      '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [{ id: 9, bankId: 7, startedAt: 'x' }] }),
      '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false, remainingSec: 300, mode: 'PRACTICE', answers: [{ questionId: 1, answer: { choiceId: 11 } }] }),
    })
    renderAt('/ielts/7')
    expect(await screen.findByRole('radio', { name: /Đúng 1/ })).toHaveAttribute('aria-checked', 'true')
    expect(calls).not.toContain('/api/banks/7/attempts')
  })

  test('lỗi tải bộ bài có nút thử lại', async () => {
    mockApi({ '/api/auth/refresh': () => json(200, auth), '/api/banks/7': () => json(500, { error: { code: 'X', message: 'Lỗi máy chủ' } }) })
    renderAt('/ielts/7')
    expect(await screen.findByRole('button', { name: /Thử lại/ })).toBeInTheDocument()
  })

  test('bộ ngoài phạm vi IELTS quay về màn luyện tập cũ', async () => {
    wire('PRACTICE', {}, bank({ skill: 'GRAMMAR_VOCAB' }))
    const router = renderAt('/ielts/7')
    await screen.findByRole('button', { name: 'Bắt đầu' })
    expect(router.state.location.pathname).toBe('/bai-tap/7')
  })
})

// Bắt thân request POST bắt đầu lượt (mockApi chỉ giữ đường dẫn).
function mockApiBody(onBody: (b: unknown) => void) {
  const inner = globalThis.fetch
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    if (url === '/api/banks/7/attempts') onBody(init?.body ? JSON.parse(String(init.body)) : undefined)
    return inner(url, init)
  })
}
