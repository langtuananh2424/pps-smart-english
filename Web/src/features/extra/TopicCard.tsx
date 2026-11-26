import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../../lib/api'
import { chip, chipTone } from '../lectures/ui'
import { startExtra } from './api'
import type { ExtraCard, ExtraKind, ExtraOverview } from './types'
import { cardLabel, challengeCount } from './view'

const card = 'flex flex-col gap-2.5 rounded-lg border border-line-subtle bg-surface p-5 shadow-edge-card'
const action = 'inline-flex min-h-11 w-full items-center justify-center rounded-sm border-2 border-line bg-surface px-4 py-2.5 text-sm font-extrabold text-brand-strong shadow-edge-neutral disabled:cursor-not-allowed disabled:opacity-60'

function Head({ c, tag }: { c: ExtraCard; tag?: React.ReactNode }) {
  return (
    <>
      <div className="flex flex-wrap gap-1.5">
        <span className={`${chip} ${chipTone.brand}`}>{cardLabel(c)}</span>
        {tag}
      </div>
      <h3 className="text-xl font-black leading-[30px] text-ink">{c.title}</h3>
    </>
  )
}

// Ôn lại: mở lại bộ Luyện tập của Topic (lượt thường, có thử lại D-150).
export function ReviewCard({ c, bankId, kind }: { c: ExtraCard; bankId: number | null; kind: ExtraKind | null }) {
  const pct = c.accuracy ?? 0
  const none = kind != null && (c.available?.[kind] ?? 0) === 0
  return (
    <article className={`${card} ${none ? 'opacity-60' : ''}`}>
      <Head c={c} tag={<span className={`${chip} ${chipTone.success}`}>Đã hoàn thành</span>} />
      <div className="h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label={`Đúng ${pct}%`}>
        <div className="h-full rounded-full bg-brand" style={{ width: `${pct}%` }} />
      </div>
      <p className="text-xs font-bold text-ink-secondary">{c.accuracy == null ? 'Chưa có điểm' : `Đúng ${c.accuracy}%`} · {c.questions ?? 0} câu</p>
      <Link to={bankId ? `/bai-tap/${bankId}` : '/bai-tap'} className={action}>Ôn lại →</Link>
    </article>
  )
}

// Thử thách: Thử sức N câu, Trả phí hoặc Mở sau khi hoàn thành X.
export function ChallengeCard({ c, o, kind }: { c: ExtraCard; o: ExtraOverview; kind: ExtraKind | null }) {
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const n = challengeCount(o, c, kind)

  const go = async () => {
    setBusy(true)
    setError(null)
    try {
      navigate(`/bai-tap/luyen-them/${(await startExtra(o.courseId, { scope: 'CHALLENGE', topicId: c.topicId, ...(kind ? { kind } : {}) })).id}`)
    } catch (e) {
      // Còn một lượt Luyện thêm đang dở: làm tiếp thay vì báo lỗi.
      const resume = e instanceof ApiError && e.code === 'attempt_in_progress' ? (e.details as { attemptId?: number } | undefined)?.attemptId : null
      if (resume) navigate(`/bai-tap/luyen-them/${resume}`)
      else setError(e instanceof Error ? e.message : 'Không bắt đầu được, thử lại nhé.')
      setBusy(false)
    }
  }

  if (c.state === 'PAID')
    return (
      <article className={`${card} bg-reward-bg`}>
        <Head c={c} tag={<span className={`${chip} ${chipTone.plain}`}>Trả phí</span>} />
        <p className="text-xs font-bold text-ink-secondary">Mở khóa Topic {c.topicNo} để làm bài tập</p>
        <Link to={`/khoa-hoc/${o.courseSlug}`} className={action}>Mở khóa →</Link>
      </article>
    )
  if (c.state === 'LOCKED')
    return (
      <article className={card}>
        <Head c={c} tag={<span className={`${chip} ${chipTone.plain}`}>Khó hơn</span>} />
        <p className="text-xs font-bold text-ink-secondary">Mở sau khi hoàn thành {c.blockedByTitle}</p>
        <Link to={`/bai-giang/khoa/${o.courseId}/unit/${c.topicId}`} className={action}>Xem trước</Link>
      </article>
    )
  return (
    <article className={card}>
      <Head c={c} tag={<span className={`${chip} ${chipTone.brand}`}>{n > 0 ? `Khó hơn · Thử sức ${n} câu` : 'Khó hơn'}</span>} />
      <p className="text-xs font-bold text-ink-secondary">{n > 0 ? 'Câu thử không tính vào tiến độ' : 'Chưa có câu thuộc dạng này'}</p>
      {error && <p role="alert" className="text-xs font-bold text-danger-fg">{error}</p>}
      <button type="button" disabled={n === 0 || busy} onClick={() => void go()} className={action}>{busy ? 'Đang tạo…' : 'Thử sức ngay →'}</button>
    </article>
  )
}
