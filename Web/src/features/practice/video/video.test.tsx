import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import type { BankGroup, Question } from '../types'
import * as api from './api'
import type { VideoGroupState } from './types'
import { VideoQuizGroup } from './VideoQuizGroup'
import { WatchPlayer } from './WatchPlayer'

const fake = vi.hoisted(() => ({ opts: null as null | { playerVars: Record<string, number>; events: { onStateChange: (e: { data: number }) => void } }, p: null as unknown }))
vi.mock('../../lectures/youtube', async (orig) => {
  const real = await orig<typeof import('../../lectures/youtube')>()
  return {
    ...real,
    loadYouTubeApi: () => Promise.resolve({
      Player: function (_el: unknown, opts: never) {
        fake.opts = opts
        fake.p = { pos: 0, getCurrentTime() { return (this as { pos: number }).pos }, getDuration: () => 100, destroy() {}, seekTo: vi.fn(), playVideo: vi.fn() }
        return fake.p
      },
    }),
  }
})

const group: BankGroup = { id: 9, title: 'Từ nối', instruction: null, passageHtml: null, sortOrder: 0, hasAudio: false, audioDurationMs: null, videoUrl: 'https://cdn/v.mp4' }
const qs: Question[] = [
  { id: 1, type: 'VIDEO_CONNECTIVE', prompt: 'Chọn từ nối', payload: {}, groupId: 9, choices: [{ id: 11, label: 'A', content: 'And' }, { id: 12, label: 'B', content: 'However' }] },
]
const base: VideoGroupState = { groupId: 9, watchedSec: 0, durationSec: 100, watched: false, submits: 0, maxSubmits: 2, finalized: false, passed: false }

function mount(g: VideoGroupState, answers: Record<number, { choiceIds: number[] } | undefined> = {}, onChange = vi.fn()) {
  vi.spyOn(api, 'fetchVideoQuiz').mockResolvedValue({ attemptNo: 1, passedAttempts: 0, requiredAttempts: 1, groups: [g] })
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><VideoQuizGroup attemptId={5} group={group} questions={qs} answers={answers} onChange={onChange} /></QueryClientProvider>)
  return onChange
}

afterEach(() => vi.restoreAllMocks())

describe('video từ nối (Figma 10.32, 11.32)', () => {
  test('chưa xem gì: màn giới thiệu, bấm Bắt đầu xem thì thấy video khóa và thẻ lượt xem hiện tại', async () => {
    mount(base)
    expect(await screen.findByText('Trả lời câu hỏi')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Bắt đầu xem' }))
    expect(screen.getByText('ĐANG XEM · KHÓA ĐIỀU KHIỂN')).toBeInTheDocument()
    expect(screen.getByText('Quiz sẽ mở ở cuối video.')).toBeInTheDocument()
    expect(screen.getByText(/0 \/ 1 lượt · Cần 1 lượt đạt/)).toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
  })

  test('đang xem dở: hiện tiến độ, quiz vẫn đóng', async () => {
    mount({ ...base, watchedSec: 40 })
    expect(await screen.findByRole('progressbar', { name: 'Tiến độ xem' })).toHaveAttribute('aria-valuenow', '40')
    expect(screen.getByText('Đang xem · 40%')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nộp câu trả lời' })).not.toBeInTheDocument()
  })

  test('xem đủ: nộp sai lần 1 thì còn 1 lần, tô đỏ lựa chọn sai, chưa lộ đáp án', async () => {
    const onChange = mount({ ...base, watched: true, watchedSec: 100 }, { 1: { choiceIds: [11] } })
    const next: VideoGroupState = { ...base, watched: true, submits: 1, results: [{ questionId: 1, correct: false }] }
    const submit = vi.spyOn(api, 'submitVideoQuiz').mockResolvedValue(next)
    expect(await screen.findByText('ĐÃ XEM 100% · LẦN NỘP 1 / 2')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('radio', { name: /However/ }))
    expect(onChange).toHaveBeenCalledWith(1, { choiceIds: [12] })
    await userEvent.click(screen.getByRole('button', { name: 'Nộp câu trả lời' }))
    expect(submit).toHaveBeenCalledWith(5, 9, [{ questionId: 1, answer: { choiceIds: [11] } }])
    expect(await screen.findByText('Còn 1 lần nộp')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /And/ }).className).toContain('!border-danger')
    expect(screen.queryByText('Lượt xem đã được tính')).not.toBeInTheDocument()
  })

  test('đã chốt: đáp án đúng xanh, giải thích của Cam, lượt chưa đạt, số lượt đã đạt', async () => {
    mount({ ...base, watched: true, finalized: true, passed: false, submits: 2, results: [{ questionId: 1, correct: false, correctAnswer: { choiceIds: [12] }, explanation: 'Vì tương phản' }] }, { 1: { choiceIds: [11] } })
    expect(await screen.findByText('ĐÃ XEM 100% · LƯỢT ĐÃ CHỐT')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /However/ }).className).toContain('!border-success-fg')
    expect(screen.getByText('Lượt xem này chưa đạt')).toBeInTheDocument()
    expect(screen.getByText('Đáp án đúng đã được mở để đối chiếu.')).toBeInTheDocument()
    expect(screen.getByText(/Vì tương phản/)).toBeInTheDocument()
    expect(screen.getByText(/Lượt 1 · Đã chốt · Đã đạt 0\/1/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nộp câu trả lời' })).not.toBeInTheDocument()
  })

  test('Đóng quiz quay về màn xem lại, mở lại được', async () => {
    mount({ ...base, watched: true })
    await userEvent.click(await screen.findByRole('button', { name: 'Đóng quiz' }))
    expect(screen.getByText('XEM LẠI · ĐIỀU KHIỂN MỞ')).toBeInTheDocument()
    expect(screen.getByText('Cần trả lời câu hỏi để chốt lượt.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Mở quiz' }))
    expect(screen.getByRole('button', { name: 'Đóng quiz' })).toBeInTheDocument()
  })

  test('lỗi tải: thông báo và thử lại', async () => {
    vi.spyOn(api, 'fetchVideoQuiz').mockRejectedValue(new Error('x'))
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><VideoQuizGroup attemptId={5} group={group} questions={qs} answers={{}} onChange={vi.fn()} /></QueryClientProvider>)
    expect(await screen.findByText('Chưa thể tải hoặc nộp câu trả lời')).toBeInTheDocument()
    expect(screen.getByText('Bạn có thể thử lại.')).toBeInTheDocument()
  })
})

describe('khóa trình phát lần xem đầu', () => {
  test('file mp4: không có thanh điều khiển, bị kéo về khi tua tới trước, tự phát lại khi dừng', () => {
    vi.spyOn(api, 'reportVideo').mockResolvedValue({ groupId: 9, watchedSec: 0, watched: false })
    render(<WatchPlayer attemptId={5} group={group} startAt={0} locked onProgress={() => {}} />)
    const v = screen.getByLabelText('Video đề') as HTMLVideoElement
    expect(v.controls).toBe(false)
    const play = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(v, 'play', { value: play })
    Object.defineProperty(v, 'ended', { value: false })
    v.currentTime = 50
    v.dispatchEvent(new Event('seeking'))
    expect(v.currentTime).toBe(0)
    v.dispatchEvent(new Event('pause'))
    expect(play).toHaveBeenCalled()
  })

  test('xem đủ thì trình phát tự do', () => {
    render(<WatchPlayer attemptId={5} group={group} startAt={0} locked={false} onProgress={() => {}} />)
    expect((screen.getByLabelText('Video đề') as HTMLVideoElement).controls).toBe(true)
  })

  test('báo tiến độ khi phát, không gửi thời lượng', async () => {
    const rep = vi.spyOn(api, 'reportVideo').mockResolvedValue({ groupId: 9, watchedSec: 0, watched: false })
    render(<WatchPlayer attemptId={5} group={group} startAt={0} locked onProgress={() => {}} />)
    screen.getByLabelText('Video đề').dispatchEvent(new Event('play'))
    await waitFor(() => expect(rep).toHaveBeenCalledWith(5, 9, 0))
  })

  test('YouTube khóa: controls:0, dừng thì phát lại, nhảy vị trí thì seekTo về chỗ đã xem xa nhất', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.spyOn(api, 'reportVideo').mockResolvedValue({ groupId: 9, watchedSec: 0, watched: false })
    render(<WatchPlayer attemptId={5} group={{ ...group, videoUrl: null, videoYoutubeId: 'dQw4w9WgXcQ' }} startAt={0} locked onProgress={() => {}} />)
    await waitFor(() => expect(fake.opts).not.toBeNull())
    expect(fake.opts!.playerVars.controls).toBe(0)
    const p = fake.p as { pos: number; seekTo: ReturnType<typeof vi.fn>; playVideo: ReturnType<typeof vi.fn> }
    p.pos = 1
    await act(async () => { await vi.advanceTimersByTimeAsync(600) })
    p.pos = 60 // nhảy tới
    await act(async () => { await vi.advanceTimersByTimeAsync(600) })
    expect(p.seekTo).toHaveBeenCalledWith(1, true)
    act(() => fake.opts!.events.onStateChange({ data: 2 })) // tạm dừng
    expect(p.playVideo).toHaveBeenCalled()
    vi.useRealTimers()
  })

  test('YouTube đã xem đủ: không khóa (player bài giảng, không controls:0)', async () => {
    fake.opts = null
    render(<WatchPlayer attemptId={5} group={{ ...group, videoUrl: null, videoYoutubeId: 'dQw4w9WgXcQ' }} startAt={0} locked={false} onProgress={() => {}} />)
    await waitFor(() => expect(fake.opts).not.toBeNull())
    expect(fake.opts!.playerVars.controls).toBeUndefined()
  })
})
