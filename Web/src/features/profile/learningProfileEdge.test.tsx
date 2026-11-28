import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'

// Tester: dữ liệu biên của hồ sơ năng lực (FR-EX-09, D-139).
const m = (key: string, label: string, o = {}) => ({ key, label, answered: 10, correct: 7, accuracy: 70, ungraded: 0, aiGraded: 0, aiAverageScore: null, ...o })
const weeks = (f: (i: number) => object = () => ({})) =>
  Array.from({ length: 12 }, (_, i) => ({ weekStart: `2026-02-${String(i + 1).padStart(2, '0')}`, answered: 5, correct: 3, accuracy: 60, ...f(i) }))
const profile = {
  answered: 20,
  accuracy: null as number | null,
  bySkill: [m('READING', 'Đọc')],
  byType: [m('FILL_TEXT', 'Điền từ')],
  byGrammar: [],
  byTopic: [],
  weeklyTrend: weeks(),
  improvements: [] as unknown[],
}
const base = { '/api/auth/refresh': () => json(200, auth) }
const show = (p: object) => {
  mockApi({ ...base, '/api/me/learning-profile': () => json(200, { ...profile, ...p }) })
  renderAt('/ho-so/nang-luc')
}

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('accuracy null ở tổng quan và kỹ năng hiện "—" / "Chưa có câu", không hiện "null%"', async () => {
  show({ bySkill: [m('LISTENING', 'Nghe', { answered: 0, correct: 0, accuracy: null })], byType: [m('MCQ', 'Trắc nghiệm', { accuracy: null })] })
  expect(await screen.findByText('Tổng quan')).toBeInTheDocument()
  expect(screen.getByText('Tỉ lệ đúng').nextElementSibling).toHaveTextContent('—')
  expect(screen.getByText('Chưa có câu')).toBeInTheDocument()
  expect(within(screen.getByRole('tabpanel')).getByText('—')).toBeInTheDocument()
  expect(document.body.textContent).not.toMatch(/null|undefined|NaN/)
})

test('mảng rỗng (kỹ năng, dạng bài, tuần) không vỡ màn', async () => {
  show({ bySkill: [], byType: [], byGrammar: [], byTopic: [], weeklyTrend: [] })
  expect(await screen.findByText('Theo kỹ năng')).toBeInTheDocument()
  expect(screen.queryAllByTestId('week-bar')).toHaveLength(0)
  expect(screen.getAllByText('Chưa có dữ liệu.')).toHaveLength(2) // thẻ kỹ năng + tab
})

test('label rỗng: dạng bài dùng nhãn của dạng, kỹ năng dùng key; label dài vẫn hiện đủ', async () => {
  const long = 'Câu điều kiện loại hai kết hợp đảo ngữ trong văn viết học thuật rất dài '.repeat(3).trim()
  show({ bySkill: [m('READING', '')], byType: [m('FILL_TEXT', '')], byGrammar: [m('COND2', long)],
    improvements: [{ dimension: 'GRAMMAR', key: 'COND2', label: long, accuracy: 10, answered: 30 }] })
  expect(await screen.findByText('READING')).toBeInTheDocument()
  expect(within(screen.getByRole('tabpanel')).queryByText('FILL_TEXT')).not.toBeInTheDocument()
  expect(screen.getByText(long)).toHaveClass('break-words')
  await userEvent.click(screen.getByRole('tab', { name: 'Ngữ pháp' }))
  expect(within(screen.getByRole('tabpanel')).getByText(long)).toHaveClass('break-words')
})

test('chỉ một tab aria-selected', async () => {
  show({})
  const tabs = await screen.findAllByRole('tab')
  expect(tabs.filter((t) => t.getAttribute('aria-selected') === 'true')).toHaveLength(1)
  await userEvent.click(tabs[2])
  expect(tabs.map((t) => t.getAttribute('aria-selected'))).toEqual(['false', 'false', 'true'])
})

test('tuần có học nhưng 0% vẫn có cột thấy được; tuần không học cột rỗng', async () => {
  show({ weeklyTrend: weeks((i) => (i === 0 ? { answered: 4, correct: 0, accuracy: 0 } : i === 1 ? { answered: 0, correct: 0, accuracy: null } : {})) })
  const bars = await screen.findAllByTestId('week-bar')
  expect(bars[0].style.height).not.toBe('')
  expect(parseFloat(bars[0].style.height)).toBeGreaterThan(0)
  expect(bars[1].style.height).toBe('')
  expect(screen.getByText('Tuần 1: 0%, 4 câu')).toBeInTheDocument()
  expect(screen.getByText('Tuần 2: không học')).toBeInTheDocument()
})

test('không có chữ "AI" trên màn (dùng "Cam")', async () => {
  show({ bySkill: [m('WRITING', 'Viết', { accuracy: null, answered: 0, ungraded: 4, aiGraded: 4, aiAverageScore: 65 })] })
  expect(await screen.findByText(/65% · Cam chấm 4 bài/)).toBeInTheDocument()
  expect(document.body.textContent).not.toMatch(/\bAI\b/)
})

test('đang tải: chưa hiện thẻ nào, không hiện lỗi', async () => {
  mockApi({ ...base, '/api/me/learning-profile': (() => new Promise(() => {})) as unknown as () => Response })
  renderAt('/ho-so/nang-luc')
  expect(await screen.findByRole('heading', { name: 'Hồ sơ năng lực', level: 1 })).toBeInTheDocument()
  expect(screen.queryByText('Tổng quan')).not.toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

test('phím mũi tên chuyển tab và dời focus', async () => {
  show({})
  const tabs = await screen.findAllByRole('tab')
  expect(tabs.map((x) => x.getAttribute('tabindex'))).toEqual(['0', '-1', '-1'])
  tabs[0].focus()
  await userEvent.keyboard('{ArrowRight}')
  expect(tabs[1]).toHaveAttribute('aria-selected', 'true')
  expect(tabs[1]).toHaveFocus()
  await userEvent.keyboard('{ArrowLeft}{ArrowLeft}')
  expect(tabs[2]).toHaveAttribute('aria-selected', 'true')
  expect(screen.getByRole('tabpanel')).toHaveAttribute('aria-labelledby', tabs[2].id)
})

// Backend: aiGraded là phần của ungraded đã có điểm Cam (LearningStatsRepository.metrics), nên "đang chấm" = ungraded - aiGraded.
test('BUG: "Câu Cam đang chấm" không đếm câu Cam đã chấm xong', async () => {
  show({ bySkill: [m('WRITING', 'Viết', { ungraded: 12, aiGraded: 12 }), m('SPEAKING', 'Nói', { ungraded: 3, aiGraded: 1 })] })
  expect(await screen.findByText('Câu Cam đang chấm')).toBeInTheDocument()
  expect(screen.getByText('Câu Cam đang chấm').nextElementSibling).toHaveTextContent(/^2$/)
})

// Backend: answered chỉ đếm câu có đúng/sai; học viên mới làm câu Viết/Nói có answered=0 nhưng đã có điểm Cam.
test('BUG: chỉ làm câu Viết/Nói đã có điểm Cam thì không hiện "Bạn chưa làm bài nào"', async () => {
  show({ answered: 0, accuracy: null, byType: [], improvements: [],
    bySkill: [m('WRITING', 'Viết', { answered: 0, correct: 0, accuracy: null, ungraded: 3, aiGraded: 3, aiAverageScore: 70 })] })
  expect(await screen.findByRole('heading', { name: 'Hồ sơ năng lực', level: 1 })).toBeInTheDocument()
  expect(await screen.findByText(/70% · Cam chấm 3 bài/)).toBeInTheDocument()
  expect(screen.queryByText(/Bạn chưa làm bài nào/)).not.toBeInTheDocument()
})
