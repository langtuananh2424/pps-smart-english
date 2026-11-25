import { QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { makeQueryClient } from '../../../lib/queryClient'
import { json, mockApi } from '../../../testUtils'
import { Player } from '../Player'
import { ResultView } from '../ResultView'
import type { AttemptResult, Bank, BankGroup, PlaybackMode } from '../types'
import { ForcedResultView } from './ForcedResultView'
import { GroupAudio } from './GroupAudio'

const err = (status: number, code: string, message = 'Lỗi máy chủ') => json(status, { error: { code, message } })
const group: BankGroup = { id: 4, title: 'Part 1', instruction: null, passageHtml: '<p>Bài đọc chung</p>', sortOrder: 1, hasAudio: true, audioDurationMs: 60_000 }
const grant = (remaining?: number) => json(200, { playbackMode: 'LOCKED', playNo: 1, playLimit: 2, remaining, url: 'https://cdn/a.mp3', expiresAt: 'x' })

// jsdom không phát audio: ghi lại phần tử để test tự bắn sự kiện.
let audios: HTMLMediaElement[] = []
beforeEach(() => {
  audios = []
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(function (this: HTMLMediaElement) {
    audios.push(this)
    this.dispatchEvent(new Event('play'))
    return Promise.resolve()
  })
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())
const endAudio = () => act(async () => void audios.at(-1)!.dispatchEvent(new Event('ended')))

const wrap = (ui: React.ReactElement) => (
  <MemoryRouter>
    <QueryClientProvider client={makeQueryClient()}>{ui}</QueryClientProvider>
  </MemoryRouter>
)
const audio = (mode: PlaybackMode, limit?: number) => <GroupAudio attemptId={9} group={group} mode={mode} defaultLimit={limit} />

describe('GroupAudio', () => {
  test('FREE: không hiện lượt, có tua, không gọi complete', async () => {
    const calls = mockApi({ '/api/attempts/9/groups/4/play': () => json(200, { playbackMode: 'FREE', url: 'u', expiresAt: 'x' }) })
    render(wrap(audio('FREE', 3)))
    expect(screen.queryByText(/lần nghe/)).not.toBeInTheDocument()
    expect(screen.getByLabelText('Tua audio')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Phát' }))
    await waitFor(() => expect(audios).toHaveLength(1))
    await endAudio()
    expect(calls.filter((c) => c.includes('complete'))).toHaveLength(0)
    expect(screen.getByRole('button', { name: 'Nghe lại' })).toBeEnabled()
  })

  test('LOCKED: khóa nút khi đang phát, bật lại sau complete, hết lượt thì khóa hẳn', async () => {
    const calls = mockApi({
      '/api/attempts/9/groups/4/play': () => grant(1),
      '/api/attempts/9/groups/4/play/complete': () => json(200, { playNo: 1, completed: true, remaining: calls.filter((c) => c.endsWith('/play')).length >= 2 ? 0 : 1 }),
    })
    render(wrap(audio('LOCKED', 2)))
    expect(screen.getByText('Tổng 2 lần nghe')).toBeInTheDocument()
    expect(screen.queryByLabelText('Tua audio')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Phát' }))
    expect(await screen.findByText('Còn 1 lần nghe')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tạm dừng' })).toBeDisabled()
    expect(screen.getByRole('progressbar')).toBeInTheDocument()
    await endAudio()
    await userEvent.click(await screen.findByRole('button', { name: 'Nghe lại' }))
    await endAudio()
    expect(await screen.findByText('Còn 0 lần nghe')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nghe lại' })).toBeDisabled()
    expect(screen.getByText('Đã hết lượt nghe')).toBeInTheDocument()
    expect(calls.filter((c) => c.endsWith('/play'))).toHaveLength(2)
  })

  test('403 play_limit_reached hiện Đã hết lượt nghe', async () => {
    mockApi({ '/api/attempts/9/groups/4/play': () => err(403, 'play_limit_reached') })
    render(wrap(audio('LOCKED')))
    await userEvent.click(screen.getByRole('button', { name: 'Phát' }))
    expect(await screen.findByText('Đã hết lượt nghe', { selector: 'p[role=status]' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('409 là thông báo nhẹ, lỗi khác là alert', async () => {
    let status = 409
    mockApi({ '/api/attempts/9/groups/4/play': () => (status === 409 ? err(409, 'play_in_progress') : err(500, 'X', 'Hỏng rồi')) })
    render(wrap(audio('LOCKED')))
    await userEvent.click(screen.getByRole('button', { name: 'Phát' }))
    expect(await screen.findByText('Đang phát, vui lòng đợi.')).toBeInTheDocument()
    status = 500
    await userEvent.click(screen.getByRole('button', { name: 'Phát' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Hỏng rồi')
  })

  test('EXAM tự phát một lần khi vào nhóm; autoplay bị chặn thì hiện Bắt đầu nghe', async () => {
    const calls = mockApi({ '/api/attempts/9/groups/4/play': () => grant(1) })
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementationOnce(() => Promise.reject(Object.assign(new Error('x'), { name: 'NotAllowedError' })))
    render(wrap(audio('EXAM', 2)))
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu nghe' }))
    expect(calls.filter((c) => c.endsWith('/play'))).toHaveLength(1)
    expect(screen.getByText('Còn 1 lần nghe')).toBeInTheDocument()
  })
})

const bank: Bank = {
  id: 7, title: 'Bài nghe', lessonId: null, isTrial: false, mode: 'PRACTICE', timeLimitSec: null, playbackMode: 'LOCKED', playLimit: 2,
  groups: [group],
  questions: [{ id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Câu một', payload: {}, groupId: 4, choices: [{ id: 5, label: 'A', content: 'So' }] }],
}

test('Player hiện bài đọc và audio của nhóm', async () => {
  mockApi({ '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false }), '/api/grammar-elements': () => json(200, []) })
  render(wrap(<Player bank={bank} attemptId={9} onDone={() => {}} />))
  expect(await screen.findByText('Bài đọc chung')).toBeInTheDocument()
  expect(screen.getByRole('region', { name: 'Audio' })).toBeInTheDocument()
  expect(screen.getByText('Tổng 2 lần nghe')).toBeInTheDocument()
})

const forced = (over = {}): AttemptResult => ({
  id: 9, bankId: 7,
  forced: { correctCount: 7, totalCount: 10, percent: 70, passPct: 80, passed: false, attemptNo: 3, hintEveryN: 3, parts: [{ groupId: 4, title: 'Part 1', correct: 7, total: 10, passPct: 80, passed: false }], hardQuestions: [{ questionId: 1, prompt: 'Câu khó', wrongCount: 2 }], ...over },
})

describe('ForcedResultView', () => {
  const view = (r: AttemptResult) => render(wrap(<ForcedResultView bank={bank} attemptId={9} forced={r.forced!} onRetry={() => {}} />))
  test('chưa đạt: số câu, phần, lần làm; không có đáp án đúng', () => {
    view(forced())
    expect(screen.getByText('Chưa đạt yêu cầu')).toBeInTheDocument()
    expect(screen.getByLabelText('Kết quả')).toHaveTextContent('7/10')
    expect(screen.getByText(/Lần làm thứ 3/)).toBeInTheDocument()
    expect(screen.getByText('Part 1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Làm lại' })).toBeInTheDocument()
    expect(screen.queryByText(/Đáp án đúng/)).not.toBeInTheDocument()
  })
  test('đạt', () => {
    view(forced({ passed: true, hardQuestions: undefined }))
    expect(screen.getByText('Đạt yêu cầu')).toBeInTheDocument()
    expect(screen.queryByText('Câu sai nhiều')).not.toBeInTheDocument()
  })
  test('Xem script gọi API và hiện script, đóng được', async () => {
    const calls = mockApi({ '/api/attempts/9/scripts/1': () => json(200, { questionId: 1, text: 'Nội dung script' }) })
    view(forced())
    await userEvent.click(screen.getByRole('button', { name: 'Xem script' }))
    expect(await screen.findByText('Nội dung script')).toBeInTheDocument()
    expect(calls).toContain('/api/attempts/9/scripts/1')
    await userEvent.click(screen.getByRole('button', { name: 'Đóng script' }))
    expect(screen.queryByText('Nội dung script')).not.toBeInTheDocument()
  })
  test('403 script_locked hiện lỗi', async () => {
    mockApi({ '/api/attempts/9/scripts/1': () => err(403, 'script_locked', 'Script chưa mở') })
    view(forced())
    await userEvent.click(screen.getByRole('button', { name: 'Xem script' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Script chưa mở')
  })
})

describe('ResultView', () => {
  const view = (initial: AttemptResult) =>
    render(wrap(<ResultView bank={bank} initial={initial} onRetry={() => {}} />))
  test('lượt forced không hiện "—" hay Chưa chấm', () => {
    mockApi({ '/api/grammar-elements': () => json(200, []) })
    view(forced())
    expect(screen.queryByText(/—/)).not.toBeInTheDocument()
    expect(screen.queryByText('Chưa chấm')).not.toBeInTheDocument()
  })
  test('kết quả thường: breakdown về sau refetch thì hiện phân tích', async () => {
    const m = { label: '', answered: 1, correct: 1, credit: 1, accuracy: 1, ungraded: 0, aiGraded: 0, aiAverageScore: null }
    mockApi({
      '/api/grammar-elements': () => json(200, [{ code: 'PASSIVE', name: 'Passive voice', colorHex: null }]),
      '/api/attempts/9': () =>
        json(200, {
          id: 9, bankId: 7, score: 1, maxScore: 1, results: [], pendingJobs: [],
          breakdown: { correct: 1, wrong: 1, ungraded: 0, bySkill: [{ ...m, key: 'LISTENING', label: 'Nghe' }], byType: [], byGrammar: [{ ...m, key: 'PASSIVE', accuracy: null }], wrongQuestions: [{ questionId: 1, wrongCount: 3 }] },
        }),
    })
    view({ id: 9, bankId: 7, score: 1, maxScore: 1, results: [], pendingJobs: [] })
    expect(screen.queryByText('Phân tích kết quả')).not.toBeInTheDocument()
    expect(await screen.findByText('Phân tích kết quả')).toBeInTheDocument()
    expect(screen.getByText('Nghe')).toBeInTheDocument()
    expect(screen.getByText('Passive voice')).toBeInTheDocument()
    expect(screen.getByText(/Sai 3 lần tích lũy/)).toBeInTheDocument()
    expect(screen.getByText(/Câu 1 · Câu một/)).toBeInTheDocument()
  })
  test.each([[true, 'Đạt yêu cầu'], [false, 'Chưa đạt yêu cầu']])('bài thường passed=%s hiện %s kèm ngưỡng', (passed, text) => {
    mockApi({ '/api/grammar-elements': () => json(200, []), '/api/attempts/9': () => json(200, { id: 9, bankId: 7, score: 1, maxScore: 2, results: [], pendingJobs: [], passed, passPct: 70 }) })
    view({ id: 9, bankId: 7, score: 1, maxScore: 2, results: [], pendingJobs: [], passed, passPct: 70 })
    expect(screen.getByText(text)).toBeInTheDocument()
    expect(screen.getByText(/Ngưỡng đạt 70%/)).toBeInTheDocument()
  })
  test('chưa có passed (còn câu AI chấm) thì không hiện Đạt/Chưa đạt', () => {
    mockApi({ '/api/grammar-elements': () => json(200, []), '/api/attempts/9': () => json(200, { id: 9, bankId: 7, results: [], pendingJobs: [5] }) })
    view({ id: 9, bankId: 7, results: [], pendingJobs: [5], passPct: 70 })
    expect(screen.queryByText(/đạt yêu cầu/i)).not.toBeInTheDocument()
  })
})
