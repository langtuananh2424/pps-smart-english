import { Link } from 'react-router'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import type { Assignment, MyResult } from './types'
import { aiPath, CANCELLED_NOTE, isCancelled } from './view'
import { useStart } from './useStart'

const card = 'rounded-xl border border-line-brand p-4'
const cardTitle = 'text-base font-black'

// Bài Nói chấm theo band; bài Viết và bộ câu hỏi theo điểm trên điểm tối đa.
const fmt = (a: Assignment, n: number, max: number | null) => (a.questionType === 'SPEAKING' ? `Band ${n}` : max ? `${n}/${max}` : String(n))

// FR-ASG-03 (Figma 480:11258, 480:11381): điểm cuối, điểm AI ban đầu, nhận xét giáo viên.
export function AssignmentResult({ a, r }: { a: Assignment; r: MyResult }) {
  const { start, busy, error } = useStart(a)
  const scored = r.finalScore != null
  // Giáo viên đã sửa điểm hoặc nhận xét: máy chủ chỉ trả finalScore khác aiScore hoặc có nhận xét.
  const graded = r.teacherComment != null || (scored && r.finalScore !== r.aiScore)
  const grading = !scored && r.maxScore != null // D-147: còn câu AI chưa chốt thì ẩn điểm
  const view = a.questionType ? aiPath(a) : r.attemptId ? `/bai-tap/${a.bankId}/ket-qua/${r.attemptId}` : null
  return (
    <div className="flex flex-col gap-4">
      <header>
        <h1 className="text-[28px] font-black leading-9 text-ink">Kết quả bài được giao</h1>
        <p className="font-semibold text-ink-secondary">{graded ? 'Giáo viên đã cập nhật điểm và nhận xét.' : 'Điểm minh họa sau khi hệ thống chấm xong.'}</p>
      </header>
      {isCancelled(a) && <p role="status" className="rounded-xl bg-muted p-3 font-bold text-ink-secondary">{CANCELLED_NOTE}</p>}
      <div className="grid gap-4 md:grid-cols-2">
        {scored && graded ? (
          <>
            <section className={`${card} bg-success-bg`}><h2 className={`${cardTitle} text-success-fg`}>Điểm cuối</h2><p className="font-semibold text-ink-secondary">{fmt(a, r.finalScore!, r.maxScore)}</p></section>
            {r.aiScore != null && <section className={`${card} bg-muted`}><h2 className={`${cardTitle} text-ink-secondary`}>Điểm Cam chấm ban đầu</h2><p className="font-semibold text-ink-secondary">{fmt(a, r.aiScore, r.maxScore)}</p></section>}
          </>
        ) : (
          <>
            <section className={`${card} bg-muted`}>
              <h2 className={`${cardTitle} text-ink-secondary`}>{scored ? 'Điểm cuối' : 'Điểm Cam chấm'}</h2>
              <p className="font-semibold text-ink-secondary">{scored ? fmt(a, r.finalScore!, r.maxScore) : grading ? 'Đang chấm…' : 'Bài này không tính điểm.'}</p>
            </section>
            <section className={`${card} bg-success-bg`}><h2 className={`${cardTitle} text-success-fg`}>Trạng thái</h2><p className="font-semibold text-ink-secondary">{r.status === 'LATE' ? 'Nộp trễ' : 'Đã nộp'}</p></section>
          </>
        )}
      </div>
      <section className={`${card} ${r.teacherComment ? 'bg-subtle' : 'bg-muted'}`}>
        <h2 className={`${cardTitle} ${r.teacherComment ? 'text-brand-strong' : 'text-ink-secondary'}`}>Nhận xét của giáo viên</h2>
        <p className="whitespace-pre-line font-semibold text-ink-secondary">{r.teacherComment ?? 'Chờ giáo viên nhận xét.'}</p>
      </section>
      {error && <p role="alert" className="text-sm font-bold text-danger">{error}</p>}
      <div className="flex flex-wrap gap-3">
        {view && <Link to={view} className={pillPrimary}>Xem bài đã nộp</Link>}
        {!scored && !grading && !a.questionType && !isCancelled(a) && <button type="button" disabled={busy} onClick={() => void start()} className={pillNeutral}>Làm lại</button>}
        <Link to="/bai-giao" className={pillNeutral}>Về bài được giao</Link>
      </div>
    </div>
  )
}
