import { useNavigate } from 'react-router'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { CANCELLED_NOTE } from '../assignments/view'
import { card, Frame } from '../writing/Frame'
import { Prompt } from './Prompt'
import type { SpeakingQuestion, SubmissionView } from './types'

const BANDS = [
  ['pronunciation', 'Phát âm'],
  ['fluency', 'Trôi chảy'],
  ['lexical', 'Từ vựng'],
  ['grammar', 'Ngữ pháp'],
] as const

const band = (n: number) => n.toFixed(1)

// Kết quả Nói (FR-AI-03, D-48): band IELTS 0–9. Figma chưa có khung kết quả nên dùng cùng thẻ với màn Viết.
export function SpeakingResultView({ question, sub, onRedo, cancelled }: { question: SpeakingQuestion; sub: SubmissionView; onRedo: () => void; cancelled?: boolean }) {
  const r = sub.result
  const navigate = useNavigate()
  const bands = r?.bands
  if (!r || !bands) return null
  const actions = (
    <>
      {cancelled
        ? <p role="status" className="font-bold text-ink-secondary">{CANCELLED_NOTE}</p> // D-153: bài đã hủy chỉ xem
        : <button type="button" onClick={onRedo} className={pillNeutral}>Nói lại</button>}
      <button type="button" onClick={() => navigate(-1)} className={`${pillPrimary} w-[220px]`}>Hoàn thành →</button>
    </>
  )
  return (
    <Frame title="Luyện nói" left={<Prompt question={question} />} actions={actions}>
      <section aria-label="Điểm" className={`${card} items-center text-center`}>
        <img src={cam} alt="" width={96} height={96} />
        <p className="text-5xl font-black text-brand-strong">{band(r.overall ?? 0)}<span className="text-2xl text-ink-muted"> / 9</span></p>
        <p className="text-sm font-extrabold text-ink-secondary">Band tổng</p>
        <ul className="grid w-full grid-cols-2 gap-3 text-left">
          {BANDS.map(([key, label]) => <Band key={key} label={label} value={bands[key]} />)}
        </ul>
      </section>

      {sub.audioUrl && (
        <section aria-label="Bản ghi của bạn" className={card}>
          <h2 className="text-lg font-black text-ink">Bản ghi của bạn</h2>
          <audio aria-label="Nghe lại audio" controls src={sub.audioUrl} className="w-full" />
        </section>
      )}

      {r.transcript && (
        <section aria-label="Bản chép lời" className={card}>
          <h2 className="text-lg font-black text-ink">Bản chép lời</h2>
          <p className="whitespace-pre-wrap text-base font-semibold text-ink">{r.transcript}</p>
        </section>
      )}

      {r.fluency && (
        <section aria-label="Độ trôi chảy" className={card}>
          <h2 className="text-lg font-black text-ink">Độ trôi chảy</h2>
          <dl className="grid grid-cols-2 gap-3">
            <Stat label="Từ/phút" value={String(Math.round(r.fluency.wordsPerMinute))} />
            <Stat label="Lần ngắt dài" value={String(r.fluency.longPauses)} />
          </dl>
        </section>
      )}

      {r.mispronounced && r.mispronounced.length > 0 && (
        <section aria-label="Từ phát âm chưa chuẩn" className={card}>
          <h2 className="text-lg font-black text-ink">Từ phát âm chưa chuẩn</h2>
          <ul className="flex flex-col gap-3">
            {r.mispronounced.map((w, i) => (
              <li key={`${w.word}-${i}`} className="flex flex-col gap-1 rounded-md border border-line p-3">
                <span className="text-base font-black text-ink">{w.word}</span>
                {(w.expectedIpa || w.heardIpa) && (
                  <span className="text-sm font-semibold text-ink-secondary">
                    {w.expectedIpa && <>Chuẩn: <b className="text-success-fg">{w.expectedIpa}</b></>}
                    {w.expectedIpa && w.heardIpa && ' · '}
                    {w.heardIpa && <>Bạn nói: <b className="text-danger-fg">{w.heardIpa}</b></>}
                  </span>
                )}
                {w.issues.length > 0 && <span className="text-sm font-semibold text-ink-secondary">{w.issues.join(', ')}</span>}
              </li>
            ))}
          </ul>
        </section>
      )}

      {r.suggestions && r.suggestions.length > 0 && (
        <section aria-label="Gợi ý diễn đạt" className={card}>
          <h2 className="text-lg font-black text-ink">Gợi ý diễn đạt</h2>
          <ul className="flex flex-col gap-3">
            {r.suggestions.map((s, i) => (
              <li key={i} className="flex flex-col gap-1.5 rounded-md border border-line p-3">
                <p className="text-base font-semibold text-ink-secondary line-through">{s.original}</p>
                <p className="rounded-sm bg-ai-bg px-2 py-1 text-base font-bold text-ink">{s.improved}</p>
                <p className="text-sm font-semibold text-ink-secondary">{s.reason}</p>
              </li>
            ))}
          </ul>
        </section>
      )}

      {r.feedback && (
        <section aria-label="Nhận xét" className={card}>
          <h2 className="text-lg font-black text-ink">Nhận xét</h2>
          <p className="whitespace-pre-wrap text-base font-semibold text-ink">{r.feedback}</p>
        </section>
      )}
    </Frame>
  )
}

function Band({ label, value }: { label: string; value: number }) {
  const pct = Math.max(0, Math.min(100, (value / 9) * 100))
  return (
    <li>
      <div className="flex justify-between text-sm font-extrabold text-ink"><span>{label}</span><span>{band(value)}</span></div>
      <div role="progressbar" aria-label={label} aria-valuenow={value} aria-valuemin={0} aria-valuemax={9} className="mt-1 h-2.5 overflow-hidden rounded-full bg-muted">
        <div className="h-full rounded-full bg-brand" style={{ width: `${pct}%` }} />
      </div>
    </li>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-subtle p-3">
      <dt className="text-[13px] font-extrabold text-ink-secondary">{label}</dt>
      <dd className="text-2xl font-black text-ink">{value}</dd>
    </div>
  )
}
