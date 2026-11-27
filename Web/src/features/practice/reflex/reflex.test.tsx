import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createRef } from 'react'
import { vi } from 'vitest'
import * as writingApi from '../../writing/api'
import type { BankGroup, Question, ReflexProgress } from '../types'
import * as reflexApi from './api'
import { ReflexSession } from './ReflexSession'
import { ReflexVideo, type ReflexApi } from './ReflexVideo'
import { isQuiet } from './volume'

const audio = vi.hoisted(() => ({ recording: false }))
vi.mock('../ai/useAudioAnswer', () => ({
  useAudioAnswer: () => ({ audio: null, converting: false, progress: null, error: null, reset: vi.fn(), send: vi.fn(), cancel: vi.fn(), convert: vi.fn(), rec: { recording: audio.recording, elapsed: 3, start: vi.fn(), stop: vi.fn(), error: null } }),
}))
vi.mock('../../speaking/useRecorder', () => ({ canRecord: () => true, useRecorder: vi.fn() }))

const group: BankGroup = { id: 9, title: 'Video', instruction: null, passageHtml: null, sortOrder: 0, hasAudio: false, audioDurationMs: null, videoUrl: 'https://cdn/v.mp4' }
const qs = (type: string): Question[] => [
  { id: 1, type, prompt: 'Câu 1', payload: { at: 45, prompt: 'What did you do after school?' }, groupId: 9 },
  { id: 2, type, prompt: 'Câu 2', payload: { at: 90, prompt: 'Why?' }, groupId: 9 },
]
const progress = (o: Partial<ReflexProgress> = {}): ReflexProgress => ({ writingFirst: false, writeUsed: 0, writeMax: 3, writePassed: false, writePending: false, speakOpen: true, recordUsed: 0, recordMax: 3, maxSeconds: 60, ...o })

let video: HTMLVideoElement
const reachMark = (sec: number) => {
  video = screen.getByLabelText('Video đề') as HTMLVideoElement
  Object.defineProperty(video, 'currentTime', { value: sec, writable: true, configurable: true })
  fireEvent.timeUpdate(video)
}

function mount(type: string, onSpoken = vi.fn()) {
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] }) }, configurable: true })
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><ReflexSession attemptId={5} group={group} questions={qs(type)} refs={{}} onSpoken={onSpoken} /></QueryClientProvider>)
  return onSpoken
}
const start = async () => {
  await userEvent.click(screen.getByRole('button', { name: 'Bắt đầu' }))
  await screen.findByText('Đang xem video. Câu hỏi sẽ mở khi video đến mốc.')
}

beforeEach(() => { audio.recording = false })
afterEach(() => vi.restoreAllMocks())

test('video phát đoạn [from, to] rồi dừng đúng ở mốc và báo lên một lần', () => {
  const apiRef = createRef<ReflexApi | null>() as { current: ReflexApi | null }
  const onMark = vi.fn()
  const play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
  const pause = vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  render(<ReflexVideo group={group} apiRef={apiRef} onMark={onMark} expectPlaying />)
  act(() => apiRef.current!.play(10, 45))
  expect(play).toHaveBeenCalled()
  reachMark(30)
  expect(onMark).not.toHaveBeenCalled()
  reachMark(45)
  expect(pause).toHaveBeenCalled()
  expect(onMark).toHaveBeenCalledTimes(1)
  reachMark(46)
  expect(onMark).toHaveBeenCalledTimes(1) // đã dừng: không báo lại
})

test('xin micro rồi xem; tới mốc thì hiện câu hỏi và nút nghe lại đoạn câu hỏi', async () => {
  vi.spyOn(reflexApi, 'fetchReflex').mockResolvedValue(progress())
  mount('VIDEO_CUE')
  expect(screen.getByText('Micro được xin trước khi mở toàn màn hình.')).toBeInTheDocument()
  await start()
  expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ audio: true })
  reachMark(45)
  expect(await screen.findByText('What did you do after school?')).toBeInTheDocument()
  expect(screen.getByText(/CÂU HỎI 1\/2 · 00:45/)).toBeInTheDocument()
  expect(screen.getByText('DỪNG TẠI MỐC CÂU HỎI')).toBeInTheDocument()
  expect(await screen.findByRole('button', { name: 'Bắt đầu ghi âm' })).toBeEnabled()
  // nghe lại: video phát lại đoạn rồi tự dừng, trong lúc đó chưa cho ghi
  await userEvent.click(screen.getByRole('button', { name: 'Nghe lại đoạn câu hỏi' }))
  expect(screen.getByText('ĐANG PHÁT ĐOẠN MẪU')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Bắt đầu ghi âm' })).toBeDisabled()
  reachMark(45)
  expect(await screen.findByText('DỪNG TẠI MỐC CÂU HỎI')).toBeInTheDocument()
})

test('micro bị từ chối thì ở lại màn bắt đầu', async () => {
  mount('VIDEO_CUE')
  ;(navigator.mediaDevices.getUserMedia as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('denied'))
  await userEvent.click(screen.getByRole('button', { name: 'Bắt đầu' }))
  expect(await screen.findByRole('alert')).toHaveTextContent(/chưa cho phép micro/)
  expect(screen.queryByLabelText('Video đề')).not.toBeInTheDocument()
})

test('đang ghi âm thì video bị khóa: dừng video, không cho nghe lại', async () => {
  vi.spyOn(reflexApi, 'fetchReflex').mockResolvedValue(progress())
  audio.recording = true
  mount('VIDEO_PICTURE')
  await start()
  reachMark(45)
  expect(await screen.findByText('ĐANG GHI ÂM · VIDEO TẠM KHÓA')).toBeInTheDocument()
  expect(screen.getByText('Video được khóa trong lúc ghi. Bạn có thể dừng sớm.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Nghe lại đoạn câu hỏi' })).toBeDisabled()
})

test('Câu ngắn: bước Nói chỉ mở sau khi viết đạt; chữ giao diện không có "AI"', async () => {
  const fetch = vi.spyOn(reflexApi, 'fetchReflex').mockResolvedValue(progress({ writingFirst: true, speakOpen: false }))
  vi.spyOn(writingApi, 'createSession').mockResolvedValue({ sessionId: 7 } as never)
  const submit = vi.spyOn(writingApi, 'submitWriting').mockResolvedValue({} as never)
  mount('VIDEO_SHORT')
  await start()
  reachMark(45)
  expect(await screen.findByText(/Đã nộp 0\/3 lần/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Bắt đầu ghi âm' })).not.toBeInTheDocument() // bước Nói chưa mở
  await userEvent.type(screen.getByLabelText('Câu trả lời của bạn'), 'I went home after school.')
  fetch.mockResolvedValue(progress({ writingFirst: true, speakOpen: false, writePending: true, writeUsed: 1 }))
  await userEvent.click(screen.getByRole('button', { name: 'Nộp câu viết' }))
  expect(submit).toHaveBeenCalledWith(7, 'I went home after school.', expect.any(String))
  expect(await screen.findByText('Cam đang chấm bài, bạn chờ chút nha.')).toBeInTheDocument()
  expect(document.body.textContent).not.toMatch(/\bAI\b/)

  fetch.mockResolvedValue(progress({ writingFirst: true, speakOpen: true, writePassed: true, writeUsed: 1 }))
  await act(async () => { await new Promise((r) => setTimeout(r, 3100)) }) // chờ nhịp hỏi lại trong lúc chấm
  expect(await screen.findByRole('button', { name: 'Bắt đầu ghi âm' }, { timeout: 4000 })).toBeEnabled()
  expect(document.body.textContent).not.toMatch(/\bAI\b/)
}, 15000)

test('cảnh báo âm lượng: bản ghi rất nhỏ bị coi là nhỏ, bản ghi to thì không', async () => {
  const wav = (peak: number) => {
    const b = new ArrayBuffer(44 + 200)
    new Int16Array(b, 44, 100).fill(peak)
    return { arrayBuffer: async () => b } as unknown as Blob
  }
  await waitFor(async () => expect(await isQuiet(wav(500))).toBe(true))
  expect(await isQuiet(wav(20000))).toBe(false)
})
