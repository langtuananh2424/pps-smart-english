import { Link, useParams } from 'react-router'
import { ApiError } from '../../lib/api'
import { BlockError, Skeleton } from '../home/BlockState'
import { btnNeutral } from '../lectures/ui'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { useMyAssignment, useMyResult } from './api'
import { AssignmentResult } from './AssignmentResult'
import type { Assignment } from './types'
import { CancelledCard } from './CancelledCard'
import { useStart } from './useStart'
import { aiPath, CANCELLED_NOTE, formatDue, isCancelled, isTodo, titleOf, typeOf } from './view'

const NotFound = () => (
  <div className="flex flex-col items-start gap-3">
    <h1 className="text-2xl font-black text-ink">Không tìm thấy bài được giao</h1>
    <Link to="/bai-giao" className={btnNeutral}>Về bài được giao</Link>
  </div>
)

function StartCard({ a }: { a: Assignment }) {
  const { start, busy, error } = useStart(a)
  return (
    <section className="mx-auto mt-6 flex max-w-lg flex-col items-center gap-3 rounded-lg border border-line-subtle bg-surface p-6 text-center shadow-edge-card">
      <span className="rounded-full bg-subtle px-3 py-1 text-xs font-extrabold text-brand-strong">{a.myStatus === 'OVERDUE' ? 'Bài đã quá hạn' : typeOf(a)}</span>
      <h1 className="text-2xl font-black text-ink">{titleOf(a)}</h1>
      <p className="font-semibold text-ink-secondary">Bài giáo viên giao · Hạn {formatDue(a.dueAt)}</p>
      {a.myStatus === 'OVERDUE' && <p className="font-semibold text-ink-secondary">Bạn vẫn có thể nộp. Bài sẽ được đánh dấu Nộp trễ.</p>}
      {a.questionType && (
        <div className="rounded-md bg-subtle p-3 text-sm font-semibold text-ink-secondary">
          <strong className="block font-extrabold text-brand-strong">Lượt AI của bài được giao</strong>
          Bài này không trừ lượt AI tuần của bạn.
        </div>
      )}
      {error && <p role="alert" className="text-sm font-bold text-danger">{error}</p>}
      <div className="flex flex-wrap justify-center gap-3">
        {a.questionType ? <Link to={aiPath(a)} className={pillPrimary}>Bắt đầu làm bài</Link>
          : <button type="button" disabled={busy} onClick={() => void start()} className={pillPrimary}>{busy ? 'Đang bắt đầu…' : 'Bắt đầu làm bài'}</button>}
        <Link to="/bai-giao" className={pillNeutral}>Về bài được giao</Link>
      </div>
    </section>
  )
}

// /lop/:classId/bai-giao/:assignmentId — đích của thông báo bài giao: chưa nộp thì bắt đầu, đã nộp thì xem kết quả.
export function AssignmentPage() {
  const p = useParams()
  const classId = Number(p.classId)
  const id = Number(p.assignmentId)
  const one = useMyAssignment(classId, id)
  const a = one.data?.classId === classId ? one.data : undefined // URL sai lớp thì như không tìm thấy
  const done = !!a && !isTodo(a.myStatus)
  const mine = useMyResult(classId, id, done)
  if (one.error instanceof ApiError && one.error.status === 404) return <NotFound />
  if (one.isError) return <BlockError onRetry={() => void one.refetch()} />
  if (one.isPending && one.fetchStatus !== 'idle') return <Skeleton className="h-64" /> // idle + pending: id không hợp lệ, không gọi API
  if (!a) return <NotFound />
  if (!done) return isCancelled(a) ? <CancelledCard a={a} /> : <StartCard a={a} />
  if (mine.isError) return <BlockError onRetry={() => void mine.refetch()} />
  return mine.data ? <AssignmentResult a={a} r={mine.data} /> : <Skeleton className="h-64" />
}
