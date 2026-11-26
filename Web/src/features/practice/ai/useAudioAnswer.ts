import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../lib/api'
import { completeSubmission, createSubmission, uploadAudioWithProgress } from '../../speaking/api'
import { audioMessage, submitMessage } from '../../speaking/errors'
import type { UploadPermit } from '../../speaking/types'
import { useRecorder } from '../../speaking/useRecorder'
import { toWav16k } from '../../speaking/wav'

export interface PickedAudio { wav: Blob; url: string; seconds: number; name?: string }

// Bản ghi của câu Nói: ghi micro hoặc chọn tệp → WAV 16 kHz → gửi (xin quyền tải → tải lên có tiến độ → xác nhận).
export function useAudioAnswer(p: { questionId: number; attemptId: number; maxSeconds: number; onQuota: (e: ApiError) => void; onSent: (submissionId: number) => void; track?: (ctl: AbortController) => () => void }) {
  const [audio, setAudio] = useState<PickedAudio | null>(null)
  const [converting, setConverting] = useState(false)
  const [progress, setProgress] = useState<number | null>(null) // null: không tải lên
  const [error, setError] = useState<string | null>(null)
  const permit = useRef<UploadPermit | null>(null) // giữ lại để gửi lại sau lỗi mạng mà không mở bản nộp mới
  const abort = useRef<AbortController | null>(null)

  const convert = async (blob: Blob, name?: string) => {
    setConverting(true)
    setError(null)
    try {
      const c = await toWav16k(blob, p.maxSeconds)
      setAudio({ ...c, url: URL.createObjectURL(c.wav), name })
    } catch (e) {
      setError(audioMessage(e, p.maxSeconds))
    } finally {
      setConverting(false)
    }
  }
  const rec = useRecorder(p.maxSeconds, (b) => void convert(b))
  useEffect(() => () => { if (audio) URL.revokeObjectURL(audio.url) }, [audio])

  const reset = () => { permit.current = null; setAudio(null); setError(null) }
  const cancel = () => abort.current?.abort()

  const send = async () => {
    if (!audio || abort.current) return // chặn bấm hai lần
    const ctl = (abort.current = new AbortController())
    const untrack = p.track?.(ctl)
    setProgress(0)
    setError(null)
    try {
      const pm = (permit.current ??= await createSubmission(p.questionId, p.attemptId))
      await uploadAudioWithProgress(pm, audio.wav, setProgress, ctl.signal)
      const r = await completeSubmission(pm.submissionId)
      permit.current = null
      p.onSent(r.submissionId)
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return // người dùng hủy: giữ bản ghi, không báo lỗi
      // Hết hạn hoặc bản ghi bị từ chối: lần sau phải xin quyền tải mới.
      if (e instanceof ApiError && (e.status === 410 || e.code.startsWith('audio_'))) permit.current = null
      if (e instanceof ApiError && e.status === 402) p.onQuota(e)
      else setError(submitMessage(e))
    } finally {
      abort.current = null
      untrack?.()
      setProgress(null)
    }
  }
  return { audio, converting, progress, error, rec, convert, reset, send, cancel }
}
