import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { fetchQuestion, fetchSubmission } from '../../speaking/api'
import { formatClock } from '../useCountdown'
import { recorderMessage } from '../../speaking/errors'
import { canRecord } from '../../speaking/useRecorder'
import { pillNeutral, pillPrimary } from '../questions/ui'
import { AnswerCard } from './AnswerCard'
import { isSent, usePublishSend, type AnswerProps } from './answer'
import { QuotaNotice, useQuotaGate } from './QuotaGate'
import { RedoDialog } from './RedoDialog'
import { useAudioAnswer } from './useAudioAnswer'

const small = '!h-11 !text-base'
const limit = (s: number) => (s % 60 === 0 ? `${s / 60} phút` : `${s} giây`)

// Câu Nói trong lượt (D-66, D-85, D-145): ghi âm hoặc tải tệp ngay trong thẻ câu, "Gửi chấm" tải lên rồi nộp. Không hiện điểm.
export function SpeakingAnswer({ question, attemptId, session, loaded, disabled, onRef, onSend, onSkip, track }: AnswerProps) {
  const sq = useQuery({ queryKey: ['speaking', 'question', question.id, attemptId], queryFn: () => fetchQuestion(question.id, undefined, attemptId), retry: false })
  const gate = useQuotaGate()
  const [editing, setEditing] = useState(false) // đang nói lại câu đã gửi
  const [redo, setRedo] = useState(false)
  const a = useAudioAnswer({ questionId: question.id, attemptId, maxSeconds: sq.data?.maxSeconds ?? 0, onQuota: gate.hit, track, onSent: (id) => { setEditing(false); onRef(id, 'GRADING') } })
  const sent = isSent(session) && !editing
  const saved = useQuery({ queryKey: ['speaking', 'submission', session?.id], queryFn: () => fetchSubmission(session!.id), enabled: sent && !a.audio, retry: false })
  const url = a.audio?.url ?? (sent ? saved.data?.audioUrl : null)
  const idle = !a.audio && !a.converting && !a.rec.recording

  const fileOnly = question.type === 'LISTEN_RECORD' // D-154 mục 4: chỉ tải tệp, không ghi trực tiếp
  usePublishSend(onSend, sent ? null : loaded && !!sq.data && !!a.audio && a.progress == null && !gate.exhausted, () => void a.send(), fileOnly ? 'Nộp bản ghi' : undefined)

  const player = url && <audio aria-label="Nghe lại bản ghi" controls src={url} className="w-full" />
  const file = a.audio?.name && <p className="text-sm font-extrabold text-ink">{a.audio.name}</p>
  let body
  if (sent) {
    body = <>{player}<button type="button" disabled={disabled} onClick={() => setRedo(true)} className={`${pillNeutral} self-start`}>Nói lại</button></>
  } else if (a.progress != null) {
    body = (
      <>
        {file}
        <div className="flex justify-between text-sm font-extrabold text-ink"><span>Đang tải lên</span><span>{a.progress}%</span></div>
        <div role="progressbar" aria-label="Tiến độ tải lên" aria-valuemin={0} aria-valuemax={100} aria-valuenow={a.progress} className="h-2 overflow-hidden rounded-full bg-muted">
          <div className="h-full rounded-full bg-brand" style={{ width: `${a.progress}%` }} />
        </div>
        <button type="button" onClick={a.cancel} className={`${pillNeutral} ${small} self-start`}>Hủy</button>
      </>
    )
  } else if (a.converting) {
    body = <p role="status" className="text-base font-extrabold text-brand-ink">Đang xử lý bản ghi…</p>
  } else if (a.rec.recording) {
    body = (
      <div className="flex flex-col gap-2 rounded-md bg-danger-bg p-3">
        <p className="flex items-baseline justify-between font-extrabold text-danger">
          <span><span aria-hidden="true" className="mr-2 inline-block size-2.5 animate-pulse rounded-full bg-danger" />Đang ghi âm</span>
          <span aria-label="Thời gian ghi" className="text-2xl font-black tabular-nums text-ink">{formatClock(Math.floor(a.rec.elapsed))}</span>
        </p>
        <button type="button" onClick={a.rec.stop} className={`${pillNeutral} ${small} self-start`}>Dừng</button>
      </div>
    )
  } else if (a.audio) {
    body = <>{file}{player}<button type="button" disabled={disabled} onClick={a.reset} className={`${pillNeutral} ${small} self-start`}>{a.audio.name ? 'Chọn tệp khác' : 'Ghi lại'}</button></>
  }
  const ready = !!sq.data && !disabled
  return (
    <>
      {gate.exhausted && !sent && <QuotaNotice gate={gate} onSkip={onSkip} />}
      {(!gate.exhausted || sent || !!a.audio) && (
        <AnswerCard title="Bản ghi của bạn" locked={sent}>
          {!sent && !!sq.data?.cueCard.length && <ul className="flex flex-col gap-1 text-base font-semibold text-ink">{sq.data.cueCard.map((c) => <li key={c}>• {c}</li>)}</ul>}
          {body}
          {idle && !sent && (
            <>
              <div className="flex flex-wrap gap-3">
                {canRecord() && !fileOnly && <button type="button" disabled={!ready || gate.exhausted} onClick={() => void a.rec.start()} className={`${pillPrimary} ${small}`}>Bắt đầu ghi âm</button>}
                <label className={`${pillNeutral} ${small} cursor-pointer focus-within:outline focus-within:outline-2 focus-within:outline-brand ${ready ? '' : 'pointer-events-none opacity-45'}`}>
                  Chọn tệp audio
                  <input type="file" accept="audio/*" className="sr-only" disabled={!ready} onChange={(e) => {
                    const f = e.target.files?.[0]
                    e.target.value = '' // chọn lại cùng tệp vẫn kích hoạt onChange
                    if (f) void a.convert(f, f.name)
                  }} />
                </label>
              </div>
              {sq.data && <p className="text-[13px] font-semibold text-ink-secondary">MP3, M4A, WAV · tối đa {limit(sq.data.maxSeconds)}</p>}
            </>
          )}
          {a.rec.error && <p role="alert" className="text-sm font-bold text-danger-fg">{recorderMessage(a.rec.error)}</p>}
        </AnswerCard>
      )}
      {a.error && <p role="alert" className="rounded-md bg-danger-bg p-3 text-sm font-bold text-danger-fg">{a.error}</p>}
      {redo && <RedoDialog title="Nói lại câu này?" confirm="Nói lại" body="Bản ghi mới sẽ thay bản ghi cũ, chỉ bản ghi mới nhất được tính điểm." onCancel={() => setRedo(false)} onConfirm={() => { a.reset(); setEditing(true); setRedo(false) }} />}
    </>
  )
}
