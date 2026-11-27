import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { formatClock } from '../useCountdown'
import { recorderMessage } from '../../speaking/errors'
import { canRecord } from '../../speaking/useRecorder'
import { QuotaNotice, useQuotaGate } from '../ai/QuotaGate'
import { useAudioAnswer } from '../ai/useAudioAnswer'
import { pillNeutral, pillPrimary } from '../questions/ui'
import { fetchReflex } from './api'
import { isQuiet } from './volume'

interface Props {
  attemptId: number
  questionId: number
  sent: boolean // đã có bản ghi nộp chấm cho câu này
  replaying: boolean // đang nghe lại đoạn câu hỏi: chưa cho ghi
  last: boolean
  onRecording: (on: boolean) => void // khóa video trong lúc ghi
  onSent: (submissionId: number) => void
  onNext: () => void
  track?: (ctl: AbortController) => () => void
}

// Bước nói (D-158): ghi tối đa N lần mỗi câu, nghe thử và cảnh báo âm lượng trước khi nộp; chỉ bản nộp mới nhất được tính.
export function ReflexSpeak({ attemptId, questionId, sent, replaying, last, onRecording, onSent, onNext, track }: Props) {
  const qc = useQueryClient()
  const key = ['attempt', attemptId, 'reflex', questionId]
  const p = useQuery({ queryKey: key, queryFn: () => fetchReflex(attemptId, questionId), retry: false })
  const gate = useQuotaGate()
  const [quiet, setQuiet] = useState(false)
  const [done, setDone] = useState(0) // id bản vừa nộp: xong thì bỏ bản ghi trong bộ nhớ để không hiện lại nút Nộp
  const d = p.data
  const a = useAudioAnswer({
    questionId, attemptId, maxSeconds: d?.maxSeconds ?? 0, onQuota: gate.hit, track,
    onSent: (id) => { setDone(id); void qc.invalidateQueries({ queryKey: key }); onSent(id) },
  })
  useEffect(() => { if (done) a.reset() }, [done]) // eslint-disable-line react-hooks/exhaustive-deps
  const recording = a.rec.recording
  useEffect(() => onRecording(recording), [recording, onRecording])
  const wav = a.audio?.wav
  useEffect(() => {
    setQuiet(false)
    if (wav) void isQuiet(wav).then(setQuiet).catch(() => {})
  }, [wav])

  const used = d?.recordUsed ?? 0
  const max = d?.recordMax ?? 0
  const left = max - used
  const ready = !!d && d.speakOpen && !replaying && !gate.exhausted

  let body
  if (a.progress != null) {
    body = (
      <>
        <div className="flex justify-between text-sm font-extrabold text-ink"><span>Đang tải lên</span><span>{a.progress}%</span></div>
        <div role="progressbar" aria-label="Tiến độ tải lên" aria-valuemin={0} aria-valuemax={100} aria-valuenow={a.progress} className="h-2 overflow-hidden rounded-full bg-muted">
          <div className="h-full rounded-full bg-brand" style={{ width: `${a.progress}%` }} />
        </div>
        <button type="button" onClick={a.cancel} className={`${pillNeutral} self-start`}>Hủy</button>
      </>
    )
  } else if (a.converting) {
    body = <p role="status" className="text-base font-extrabold text-brand-ink">Đang xử lý bản ghi…</p>
  } else if (recording) {
    body = (
      <div className="flex flex-col gap-2 rounded-md bg-danger-bg p-3">
        <p className="flex items-baseline justify-between font-extrabold text-danger">
          <span>Đang ghi âm · <span aria-label="Thời gian ghi">{formatClock(Math.floor(a.rec.elapsed))}</span> / {d?.maxSeconds}s</span>
        </p>
        <p className="text-sm font-semibold text-ink-secondary">Video được khóa trong lúc ghi. Bạn có thể dừng sớm.</p>
        <button type="button" onClick={a.rec.stop} className={`${pillNeutral} self-start`}>Dừng</button>
      </div>
    )
  } else if (a.audio) {
    body = (
      <>
        <audio aria-label="Nghe thử bản ghi" controls src={a.audio.url} className="w-full" />
        {quiet && <p role="status" className="rounded-md bg-reward-bg px-3.5 py-2 text-sm font-bold text-reward-fg">Âm lượng hơi nhỏ. Bạn vẫn có thể nộp hoặc ghi âm lại.</p>}
        <p className="text-[13px] font-extrabold text-ink-secondary">Đã ghi âm {used + 1}/{max} lần</p>
        <div className="flex flex-wrap gap-3">
          <button type="button" onClick={() => void a.send()} className={pillPrimary}>Nộp bản ghi</button>
          <button type="button" onClick={a.reset} className={pillNeutral}>Ghi lại</button>
        </div>
      </>
    )
  } else if (sent) {
    body = (
      <>
        <p role="status" className="rounded-md bg-success-bg px-3.5 py-2 text-sm font-bold text-success-fg">Đã nộp bản ghi {used}/{max} · Cam đang chấm bài. Kết quả hiện sau khi nộp bài.</p>
        <div className="flex flex-wrap gap-3">
          {left > 0 && <button type="button" disabled={!ready} onClick={() => void a.rec.start()} className={pillNeutral}>Ghi âm lại ({left} lần còn lại)</button>}
          <button type="button" onClick={onNext} className={pillPrimary}>{last ? 'Hoàn thành' : 'Câu tiếp theo'}</button>
        </div>
      </>
    )
  } else {
    body = (
      <>
        <p className="font-extrabold text-ink">Sẵn sàng ghi âm</p>
        <p className="text-[13px] font-semibold text-ink-secondary">Thời lượng tối đa: {d?.maxSeconds ?? '—'}s · Lượt ghi âm {Math.min(used + 1, max)}/{max || '—'}</p>
        {!canRecord() && <p role="alert" className="text-sm font-bold text-danger-fg">Trình duyệt này chưa hỗ trợ ghi âm.</p>}
        <button type="button" disabled={!ready || !canRecord()} onClick={() => void a.rec.start()} className={`${pillPrimary} self-start`}>Bắt đầu ghi âm</button>
      </>
    )
  }
  return (
    <section aria-label="Bước nói" className="flex flex-col gap-3">
      {gate.exhausted && !sent && <QuotaNotice gate={gate} onSkip={onNext} />}
      {body}
      {a.rec.error && <p role="alert" className="text-sm font-bold text-danger-fg">{recorderMessage(a.rec.error)}</p>}
      {a.error && <p role="alert" className="rounded-md bg-danger-bg p-3 text-sm font-bold text-danger-fg">{a.error}</p>}
    </section>
  )
}
