import { QueryClientProvider } from '@tanstack/react-query'
import { act, render, renderHook, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { setAccessToken } from '../../lib/api'
import { makeQueryClient } from '../../lib/queryClient'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'
import { describeAnswer } from './describe'
import { rendererOf, isAnswered } from './questions/registry'
import { ResultView, formatScore } from './ResultView'
import type { Answer, AttemptResult, Bank, Question } from './types'
import { formatClock, useCountdown } from './useCountdown'

const q = (type: string, payload: unknown, extra: Partial<Question> = {}): Question => ({ id: 1, type, prompt: 'P', payload, ...extra })

// Dựng một renderer, trả mảng các giá trị onChange đã phát ra.
const elements = [
  { code: 'INVERSION', name: 'Inversion', colorHex: '#6d28d9' },
  { code: 'RELATIVE_CLAUSE', name: 'Relative clause', colorHex: '#255a78' },
  { code: 'PASSIVE', name: 'Passive voice', colorHex: null },
]
const withClient = (ui: React.ReactElement) => <MemoryRouter><QueryClientProvider client={makeQueryClient()}>{ui}</QueryClientProvider></MemoryRouter>

function mount(question: Question, initial?: Answer) {
  mockApi({ '/api/grammar-elements': () => json(200, elements) })
  const out: (Answer | undefined)[] = []
  const { Component } = rendererOf(question.type)
  let value = initial
  const ui = () => <Component question={question} value={value} onChange={(v) => { value = v; out.push(v); view.rerender(withClient(ui())) }} />
  const view = render(withClient(ui()))
  return out
}

describe('renderer tạo đúng answer', () => {
  test('MCQ', async () => {
    const out = mount(q('CONNECTIVE_MCQ', {}, { choices: [{ id: 5, label: 'A', content: 'However' }, { id: 6, label: 'B', content: 'Because' }] }))
    await userEvent.click(screen.getByRole('radio', { name: /Because/ }))
    expect(out.at(-1)).toEqual({ choiceId: 6 })
  })
  test('MATCH: chọn vế còn lại, bỏ chọn thì xóa cặp', async () => {
    const out = mount(q('CONNECTIVE_MATCH', { left: [{ id: 'a', text: 'Rain' }], right: [{ id: 'x', text: 'so' }, { id: 'y', text: 'but' }] }))
    await userEvent.selectOptions(screen.getByLabelText(/Vế còn lại/), 'but')
    expect(out.at(-1)).toEqual({ pairs: { a: 'y' } })
    await userEvent.selectOptions(screen.getByLabelText(/Vế còn lại/), '')
    expect(out.at(-1)).toBeUndefined()
  })
  test('CLOZE', async () => {
    const out = mount(q('CONNECTIVE_CLOZE', { text: 'It rained {1} we stayed.', blanks: ['1'] }))
    await userEvent.type(screen.getByLabelText('Chỗ trống 1'), 'so')
    expect(out.at(-1)).toEqual({ blanks: { 1: 'so' } })
  })
  test('CLOZE dùng labels làm tên ô, thiếu thì "Chỗ trống id"', () => {
    mount(q('CONNECTIVE_CLOZE', { text: 'A {1} b {2}.', blanks: ['1', '2'], labels: { 1: 'Từ nối chỉ nguyên nhân' } }))
    expect(screen.getByLabelText('Từ nối chỉ nguyên nhân')).toBeInTheDocument()
    expect(screen.getByLabelText('Chỗ trống 2')).toBeInTheDocument()
  })
  test.each(['CONNECTIVE_REWRITE', 'GRAMMAR_REWRITE'])('%s', async (type) => {
    const out = mount(q(type, { sentence: 'S', requiredWord: 'although', requiredElement: 'PASSIVE' }))
    await userEvent.type(screen.getByLabelText('Câu trả lời của bạn'), 'Hi')
    expect(out.at(-1)).toEqual({ text: 'Hi' })
  })
  test('IDENTIFY: chọn cụm rồi gắn nhãn, index từ 0 và end tính cả', async () => {
    const out = mount(q('GRAMMAR_IDENTIFY', { tokens: ['The', 'book', 'was', 'read'], spanCount: 1 }))
    await userEvent.click(screen.getByRole('button', { name: 'was' }))
    await userEvent.click(screen.getByRole('button', { name: 'read' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Passive voice' }))
    expect(out.at(-1)).toEqual({ spans: [{ start: 2, end: 3, element: 'PASSIVE' }] })
  })
  test('SPOT_FIX', async () => {
    const out = mount(q('GRAMMAR_SPOT_FIX', { tokens: ['He', 'go', 'home'] }))
    await userEvent.click(screen.getByRole('radio', { name: /go/ }))
    expect(isAnswered('GRAMMAR_SPOT_FIX', out.at(-1))).toBe(false) // mới chọn từ, chưa nhập bản sửa
    await userEvent.type(screen.getByRole('textbox'), 'goes')
    expect(out.at(-1)).toEqual({ position: 1, correction: 'goes' })
    expect(isAnswered('GRAMMAR_SPOT_FIX', out.at(-1))).toBe(true)
  })
  test('BUILD: thêm và bỏ khối', async () => {
    const out = mount(q('GRAMMAR_BUILD', { blocks: [{ id: 'b1', text: 'I' }, { id: 'b2', text: 'run' }] }))
    await userEvent.click(screen.getByRole('button', { name: 'run' }))
    await userEvent.click(screen.getByRole('button', { name: 'I' }))
    expect(out.at(-1)).toEqual({ order: ['b2', 'b1'] })
    await userEvent.click(screen.getByRole('button', { name: 'Bỏ khối run' }))
    expect(out.at(-1)).toEqual({ order: ['b1'] })
  })
  test('WRITING không có đáp án và không tính câu chưa làm', () => {
    mount(q('WRITING', {}, { id: 42 }))
    expect(screen.getByRole('link', { name: 'Làm ở màn Viết' })).toHaveAttribute('href', '/viet/42')
    expect(isAnswered('WRITING', { text: 'x' })).toBe(false)
  })
  test('SPEAKING dẫn tới màn Nói', () => {
    mount(q('SPEAKING', {}, { id: 43 }))
    expect(screen.getByRole('link', { name: 'Làm ở màn Nói' })).toHaveAttribute('href', '/noi/43')
  })
})

describe('mô tả đáp án ở màn kết quả', () => {
  test('cloze đáp án đúng nhiều lựa chọn, spot-fix, identify', () => {
    expect(describeAnswer(q('CONNECTIVE_CLOZE', {}), { blanks: { 1: ['so', 'thus'] } })).toBe('1: so / thus')
    expect(describeAnswer(q('GRAMMAR_SPOT_FIX', { tokens: ['He', 'go'] }), { position: 1, corrections: ['goes'] })).toBe('go → goes')
    expect(describeAnswer(q('GRAMMAR_IDENTIFY', { tokens: ['a', 'b'] }), { spans: [{ start: 0, end: 1, element: 'PASSIVE' }] }, () => 'Passive voice')).toBe('a b (Passive voice)')
  })
})

describe('đồng hồ', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  test('đếm ngược và gọi hết giờ đúng một lần', () => {
    const onExpire = vi.fn()
    const { result } = renderHook(() => useCountdown(3, false, 1, onExpire))
    expect(result.current).toBe(3)
    act(() => vi.advanceTimersByTime(2000))
    expect(result.current).toBe(1)
    act(() => vi.advanceTimersByTime(5000))
    expect(result.current).toBe(0)
    expect(onExpire).toHaveBeenCalledTimes(1)
  })
  test('tạm dừng thì đứng yên; đồng bộ lại thì lấy số của server', () => {
    const onExpire = vi.fn()
    const { result, rerender } = renderHook(({ s, p, k }) => useCountdown(s, p, k, onExpire), { initialProps: { s: 10, p: false, k: 1 } })
    act(() => vi.advanceTimersByTime(3000))
    rerender({ s: 7, p: true, k: 2 })
    act(() => vi.advanceTimersByTime(60_000))
    expect(result.current).toBe(7)
    expect(onExpire).not.toHaveBeenCalled()
    rerender({ s: 20, p: false, k: 3 })
    expect(result.current).toBe(20)
  })
  test('định dạng mm:ss', () => expect(formatClock(605)).toBe('10:05'))
})

const bank: Bank = {
  id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null,
  questions: [
    q('CONNECTIVE_MCQ', {}, { id: 1, instruction: 'Chọn từ nối thích hợp.', prompt: 'Chọn', choices: [{ id: 5, label: 'A', content: 'So' }] }),
    q('GRAMMAR_REWRITE', { sentence: 's' }, { id: 2, prompt: 'Viết lại' }),
    q('WRITING', {}, { id: 3, prompt: 'Viết bài' }),
  ],
}
const result = (extra: Partial<AttemptResult> = {}): AttemptResult => ({
  id: 9, bankId: 7, score: 1.5, maxScore: 2,
  results: [
    { questionId: 1, isCorrect: true, correctAnswer: { choiceId: 5 }, explanation: 'Vì so' },
    { questionId: 2, isCorrect: null },
  ],
  pendingJobs: [], ...extra,
})

describe('màn kết quả', () => {
  const view = (r: AttemptResult) => {
    mockApi({ '/api/grammar-elements': () => json(200, elements) })
    return render(
      <QueryClientProvider client={makeQueryClient()}>
        <MemoryRouter>
          <ResultView bank={bank} initial={r} answers={{ 1: { choiceId: 5 } }} onRetry={() => {}} />
        </MemoryRouter>
      </QueryClientProvider>,
    )
  }
  test('điểm giữ số lẻ, đúng/sai, bài làm, đáp án, giải thích; Viết không tính', () => {
    expect(formatScore(1.456)).toBe('1.46')
    expect(formatScore(2)).toBe('2')
    view(result())
    expect(screen.getByLabelText('Điểm')).toHaveTextContent('1.5/2')
    expect(screen.getByText('Đúng')).toBeInTheDocument()
    expect(screen.getByText('Vì so')).toBeInTheDocument()
    expect(screen.getAllByText('A. So')).toHaveLength(2)
    expect(screen.getByText(/không tính điểm ở đây/)).toBeInTheDocument()
  })
  test('không có bài làm trong phiên thì ẩn dòng Bài làm', () => {
    mockApi({ '/api/grammar-elements': () => json(200, elements) })
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <MemoryRouter>
          <ResultView bank={bank} initial={result()} onRetry={() => {}} />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    expect(screen.queryByText('Bài làm:')).not.toBeInTheDocument()
    expect(screen.getByText('Đáp án đúng:')).toBeInTheDocument()
  })
  test('đang chấm AI và hết lượt AI', () => {
    view(result({ pendingJobs: [3], aiQuotaRequired: true, products: [{ id: 1, name: 'Gói 10 lượt' }] }))
    expect(screen.getByText('Đang chấm…')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Gói 10 lượt')
  })
})

describe('trang làm bài', () => {
  beforeEach(() => setAccessToken('t'))
  const wire = (over: Record<string, (b: unknown) => Response> = {}) =>
    mockApi({
      '/api/auth/refresh': () => json(200, auth),
      '/api/grammar-elements': () => json(200, elements),
      '/api/banks/7': () => json(200, bank),
      '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [] }),
      '/api/banks/7/attempts': () => json(201, { id: 9, bankId: 7, startedAt: 'x', timeLimitSec: null }),
      '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false }),
      '/api/attempts/9/submit': () => json(200, result()),
      ...over,
    })

  test('chỉ tạo lượt khi bấm Bắt đầu, làm rồi nộp ra kết quả', async () => {
    const calls = wire()
    renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await userEvent.click(await screen.findByRole('radio', { name: /So/ }))
    expect(screen.getByText(/Chọn từ nối thích hợp\./)).toBeInTheDocument() // instruction thay tên dạng
    await userEvent.click(screen.getByRole('button', { name: 'Câu 3' }))
    await userEvent.click(screen.getByRole('button', { name: 'Nộp bài' }))
    expect(screen.getByText(/Bạn còn 1 câu chưa làm/)).toBeInTheDocument() // Viết không tính, câu 2 chưa làm
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Nộp bài' }))
    expect(await screen.findByLabelText('Điểm')).toHaveTextContent('1.5/2')
    expect(calls.filter((c) => c === '/api/banks/7/attempts')).toHaveLength(1)
  })

  test('có lượt dở thì vào làm tiếp, nạp nháp, không tạo lượt mới', async () => {
    const calls = wire({
      '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [{ id: 9, bankId: 7, startedAt: 'x' }] }),
      '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false, answers: [{ questionId: 1, answer: { choiceId: 5 } }] }),
    })
    renderAt('/bai-tap/7')
    expect(await screen.findByRole('radio', { name: /So/ })).toHaveAttribute('aria-checked', 'true')
    expect(calls).not.toContain('/api/banks/7/attempts')
  })

  test.each([
    ['bank_locked', 403, 'Bộ bài tập đang khóa'],
    ['bank_not_found', 404, 'Không tìm thấy bộ bài tập'],
  ])('lỗi %s', async (code, status, title) => {
    wire({ '/api/banks/7': () => json(status, { error: { code, message: 'x' } }) })
    renderAt('/bai-tap/7')
    expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument()
  })

  test('bài thi chưa đạt tiến độ nêu cần bao nhiêu phần trăm', async () => {
    wire({ '/api/banks/7': () => json(403, { error: { code: 'exam_locked', message: 'x', details: { progressPercent: 39, requiredPercent: 80 } } }) })
    renderAt('/bai-tap/7')
    expect(await screen.findByText(/≥ 80% .* hiện 39%/)).toBeInTheDocument()
  })

  test('nộp lỗi 503 thì báo và cho nộp lại', async () => {
    wire({ '/api/attempts/9/submit': () => apiError(503, 'bận') })
    renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Câu 3' }))
    await userEvent.click(screen.getByRole('button', { name: 'Nộp bài' }))
    const dialog = await screen.findByRole('alertdialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Nộp bài' }))
    await waitFor(() => expect(screen.getByRole('alertdialog')).toHaveTextContent('Hệ thống đang bận'))
    expect(within(dialog).getByRole('button', { name: 'Nộp bài' })).not.toBeDisabled()
  })
})
