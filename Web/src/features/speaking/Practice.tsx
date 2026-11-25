import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../lib/api'
import { pillPrimary } from '../practice/questions/ui'
import { QuotaCard } from '../writing/errors'
import { Frame } from '../writing/Frame'
import { completeSubmission, createSubmission, uploadAudio } from './api'
import { audioMessage, BlockingError, isBlocking, submitMessage } from './errors'
import { Prompt } from './Prompt'
import { Recorder, type Audio } from './Recorder'
import type { SpeakingQuestion, UploadPermit } from './types'
import { useRecorder } from './useRecorder'
import { toWav16k } from './wav'

// Chưa có bản nộp: ghi âm hoặc chọn tệp → WAV 16 kHz → nghe lại → nộp (create → upload → complete).
export function Practice({ question, notice, attemptId, onSubmitted }: { question: SpeakingQuestion; notice?: string; attemptId?: number; onSubmitted: (id: number, jobId: number | null) => void }) {
  const [audio, setAudio] = useState<Audio | null>(null)
  const [converting, setConverting] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [blocked, setBlocked] = useState<ApiError | null>(null)
  const permit = useRef<UploadPermit | null>(null) // giữ lại để thử lại sau lỗi mạng mà không tạo lượt mới
  const busy = useRef(false)

  const convert = async (blob: Blob) => {
    setConverting(true)
    setError(null)
    try {
      const c = await toWav16k(blob, question.maxSeconds)
      setAudio({ ...c, url: URL.createObjectURL(c.wav) })
    } catch (e) {
      setError(audioMessage(e, question.maxSeconds))
    } finally {
      setConverting(false)
    }
  }
  const rec = useRecorder(question.maxSeconds, (b) => void convert(b))

  useEffect(() => () => { if (audio) URL.revokeObjectURL(audio.url) }, [audio])

  const submit = async () => {
    if (!audio || busy.current) return // chặn bấm hai lần
    busy.current = true
    setUploading(true)
    setError(null)
    try {
      const p = (permit.current ??= await createSubmission(question.id, attemptId))
      await uploadAudio(p, audio.wav)
      const r = await completeSubmission(p.submissionId)
      permit.current = null
      onSubmitted(r.submissionId, r.jobId)
    } catch (e) {
      // Hết hạn hoặc bản ghi bị từ chối: lần sau phải xin quyền tải mới.
      if (e instanceof ApiError && (e.status === 410 || e.code.startsWith('audio_'))) permit.current = null
      if (isBlocking(e)) setBlocked(e)
      else setError(submitMessage(e))
    } finally {
      busy.current = false
      setUploading(false)
    }
  }

  if (blocked) return blocked.status === 402 ? <Frame title="Luyện nói"><QuotaCard error={blocked} /></Frame> : <BlockingError error={blocked} />

  const actions = (
    <button type="button" disabled={!audio || uploading} onClick={() => void submit()} className={`${pillPrimary} w-full md:w-[220px]`}>
      {uploading ? 'Đang tải lên…' : 'Nộp bản ghi →'}
    </button>
  )
  return (
    <Frame title="Luyện nói" actions={actions}>
      {notice && <p role="note" className="rounded-md bg-reward-bg p-4 text-sm font-bold text-reward-fg">{notice}</p>}
      <Prompt question={question}>
        <Recorder
          maxSeconds={question.maxSeconds} audio={audio} converting={converting} recording={rec.recording} elapsed={rec.elapsed}
          recorderError={rec.error} disabled={uploading} onStart={() => void rec.start()} onStop={rec.stop}
          onPick={(f) => void convert(f)} onReset={() => { permit.current = null; setAudio(null); setError(null) }}
        />
        {uploading && <p role="status" className="flex items-center gap-3 text-base font-extrabold text-brand-ink">
          <span aria-hidden="true" className="size-5 animate-spin rounded-full border-2 border-brand border-t-transparent" />Đang tải lên…
        </p>}
        {error && <p role="alert" className="rounded-md bg-danger-bg p-3 text-sm font-bold text-danger-fg">{error}</p>}
      </Prompt>
    </Frame>
  )
}
