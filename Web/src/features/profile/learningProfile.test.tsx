import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'

const m = (key: string, label: string, o = {}) => ({ key, label, answered: 10, correct: 7, accuracy: 70, ungraded: 0, aiGraded: 0, aiAverageScore: null, ...o })
const weeks = Array.from({ length: 12 }, (_, i) => ({ weekStart: `2026-01-${String(i + 1).padStart(2, '0')}`, answered: i === 2 ? 0 : 5, correct: 3, accuracy: i === 2 ? null : 60 }))
const profile = {
  answered: 539,
  accuracy: 70,
  bySkill: [
    m('READING', 'Đọc', { accuracy: 74, answered: 168 }),
    m('WRITING', 'Viết', { accuracy: 66, ungraded: 15, aiGraded: 12 }),
    m('SPEAKING', 'Nói', { accuracy: null, answered: 0, ungraded: 2, aiGraded: 2, aiAverageScore: 75 }),
    m('UNSPECIFIED', 'Khác'),
  ],
  byType: [m('FILL_TEXT', 'Điền từ', { answered: 74, accuracy: 71 })],
  byGrammar: [m('PASSIVE', 'Câu bị động', { answered: 17, accuracy: 50 })],
  byTopic: [],
  weeklyTrend: weeks,
  improvements: [{ dimension: 'GRAMMAR', key: 'PASSIVE', label: 'Câu bị động', accuracy: 50, answered: 17 }],
}
const base = { '/api/auth/refresh': () => json(200, auth) }

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('hiện đủ các thẻ, bỏ UNSPECIFIED, tuần không học để cột rỗng', async () => {
  mockApi({ ...base, '/api/me/learning-profile': () => json(200, profile) })
  renderAt('/ho-so/nang-luc')
  expect(await screen.findByRole('heading', { name: 'Hồ sơ năng lực', level: 1 })).toBeInTheDocument()
  expect(await screen.findByText('539')).toBeInTheDocument()
  expect(screen.getByText('70%')).toBeInTheDocument()
  expect(screen.getByText('Câu Cam đang chấm').nextElementSibling).toHaveTextContent('3')
  expect(screen.getByText('17 câu đã làm')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Luyện thêm phần này' })).toHaveAttribute('href', '/bai-tap')
  expect(screen.getAllByTestId('week-bar')).toHaveLength(12)
  expect(screen.getByText('Tuần 3: không học')).toBeInTheDocument()
  expect(screen.getByText(/74% · 168 câu/)).toBeInTheDocument()
  expect(screen.getByText(/Cam chấm 12 bài/)).toBeInTheDocument()
  expect(screen.getByText('75% · Cam chấm 2 bài')).toBeInTheDocument()
  expect(screen.queryByText('Khác')).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Về hồ sơ' })).toHaveAttribute('href', '/ho-so')
})

test('tab chuyển giữa dạng bài, ngữ pháp, chủ đề', async () => {
  mockApi({ ...base, '/api/me/learning-profile': () => json(200, profile) })
  renderAt('/ho-so/nang-luc')
  const tabs = within(await screen.findByRole('tablist'))
  expect(tabs.getByRole('tab', { name: 'Dạng bài' })).toHaveAttribute('aria-selected', 'true')
  expect(screen.getByText('Điền từ')).toBeInTheDocument()
  await userEvent.click(tabs.getByRole('tab', { name: 'Ngữ pháp' }))
  expect(tabs.getByRole('tab', { name: 'Ngữ pháp' })).toHaveAttribute('aria-selected', 'true')
  expect(within(screen.getByRole('tabpanel')).getByText('Câu bị động')).toBeInTheDocument()
  await userEvent.click(tabs.getByRole('tab', { name: 'Chủ đề' }))
  expect(within(screen.getByRole('tabpanel')).getByText('Chưa có dữ liệu.')).toBeInTheDocument()
  expect(screen.getByText('Chưa có dữ liệu.')).toBeInTheDocument()
})

test('chưa có gợi ý cải thiện thì hiện câu giải thích', async () => {
  mockApi({ ...base, '/api/me/learning-profile': () => json(200, { ...profile, improvements: [] }) })
  renderAt('/ho-so/nang-luc')
  expect(await screen.findByText(/Chưa đủ dữ liệu, hãy làm thêm câu/)).toBeInTheDocument()
})

test('trống: answered=0 hiện lời mời và nút tới bài tập', async () => {
  mockApi({ ...base, '/api/me/learning-profile': () => json(200, { ...profile, answered: 0, accuracy: null, bySkill: [m('READING', 'Đọc', { answered: 0, accuracy: null })] }) })
  renderAt('/ho-so/nang-luc')
  expect(await screen.findByText(/Bạn chưa làm bài nào/)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Đến bài tập' })).toHaveAttribute('href', '/bai-tap')
})

test('lỗi: có nút thử lại và tải lại được', async () => {
  let fail = true
  mockApi({ ...base, '/api/me/learning-profile': () => (fail ? apiError(400, 'x') : json(200, profile)) })
  renderAt('/ho-so/nang-luc')
  const retry = await screen.findByRole('button', { name: 'Thử lại' })
  fail = false
  await userEvent.click(retry)
  expect(await screen.findByText('539')).toBeInTheDocument()
})
