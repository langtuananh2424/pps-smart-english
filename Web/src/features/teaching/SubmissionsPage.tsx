import { useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../lib/api'
import { PageHead, Section, WriteError } from '../admin/ui'
import { useAssignmentProgress, useCancelAssignment, useClassAssignments, useSetMode, type AsgMode, type AsgProgressRow, type TeachAssignment } from './api'
import { asgState, asgTitle, ASG_MODE_LABEL, classPath, fmtTime, KICKER, PROGRESS_LABEL, score } from './format'
import { Confirm, Gate, NavLinkBtn, Pill, td, th, useNum } from './ui'

const camScore = (r: AsgProgressRow) => {
  if (r.aiScore != null) return score(r.aiScore, r.maxScore)
  return !r.forced && (r.status === 'SUBMITTED' || r.status === 'LATE') ? 'Cam đang chấm' : '—'
}

function ModeBox({ a, locked }: { a: TeachAssignment; locked: boolean }) {
  const set = useSetMode(a.classId, a.id)
  const e = set.error
  return (
    <fieldset disabled={locked || set.isPending} className="flex flex-col gap-1">
      <legend className="text-sm font-extrabold text-ink">Chế độ làm bài</legend>
      <div className="flex flex-wrap gap-4">
        {(['PRACTICE', 'EXAM'] as const).map((m: AsgMode) => (
          <label key={m} className="flex min-h-11 items-center gap-2 font-bold text-ink">
            <input type="radio" name="asg-mode" checked={a.mode === m} onChange={() => set.mutate(m)} className="size-5 accent-brand-strong" />
            {ASG_MODE_LABEL[m]}
          </label>
        ))}
      </div>
      {locked && <p className="text-sm font-semibold text-ink-secondary">Đã có học viên bắt đầu làm nên không đổi chế độ được.</p>}
      {e && (e instanceof ApiError && e.code === 'assignment_mode_locked'
        ? <p role="alert" className="font-bold text-danger-fg">Đã có học viên bắt đầu làm, không đổi chế độ được.</p>
        : <WriteError error={e} />)}
    </fieldset>
  )
}

// #61, FR-ASG-04: tiến độ nộp bài; chấm đi từ đây.
export function SubmissionsPage() {
  const classId = useNum('classId')
  const id = useNum('assignmentId')
  const list = useClassAssignments(classId)
  const rows = useAssignmentProgress(classId, id)
  const cancel = useCancelAssignment(classId, id)
  const [asking, setAsking] = useState(false)
  const a = list.data?.find((x) => x.id === id)
  const cancelled = a?.status === 'CANCELLED'
  const st = a && asgState(a)
  return (
    <>
      <PageHead kicker={KICKER} title={a ? asgTitle(a) : 'Tiến độ nộp bài'} sub={a ? `Hạn nộp ${fmtTime(a.dueAt)}` : undefined} />
      {st && <Pill danger={st.danger}>{st.label}</Pill>}
      {a && a.questionId == null && !cancelled && <ModeBox a={a} locked={!!rows.data?.some((r) => r.attemptId != null)} />}
      {a && a.questionId == null && cancelled && a.mode && <p className="font-semibold text-ink">Chế độ: {ASG_MODE_LABEL[a.mode]}</p>}
      <Section title="Tiến độ nộp bài">
        <Gate q={rows}>
          {(rs) => (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] border-collapse">
                <caption className="sr-only">Tiến độ nộp bài</caption>
                <thead className="bg-muted">
                  <tr>{['Học viên', 'Trạng thái', 'Điểm Cam chấm', 'Điểm cuối', ''].map((h, i) => <th key={i} scope="col" className={th}>{h || <span className="sr-only">Mở bài nộp</span>}</th>)}</tr>
                </thead>
                <tbody>
                  {rs.map((r) => (
                    <tr key={r.userId} className="border-b border-line-brand last:border-b-0">
                      <td className={td}>{r.displayName}</td>
                      <td className={td}><Pill danger={r.status === 'LATE' || r.status === 'OVERDUE'}>{PROGRESS_LABEL[r.status]}</Pill></td>
                      <td className={td}>{camScore(r)}</td>
                      <td className={td}>{score(r.finalScore, r.maxScore)}</td>
                      <td className={td}>
                        {r.attemptId != null && (
                          <Link to={classPath(classId, `/bai-giao/${id}/bai-nop/${r.attemptId}`)} aria-label={`Mở bài nộp của ${r.displayName}`} className="min-h-11 font-extrabold text-brand-strong underline">
                            Mở bài nộp
                          </Link>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Gate>
      </Section>
      <div className="flex flex-wrap gap-2">
        {a && !cancelled && (
          <button type="button" onClick={() => setAsking(true)} className="inline-flex h-12 items-center rounded-sm border border-danger px-4 font-extrabold text-danger-fg">Hủy bài giao</button>
        )}
        <NavLinkBtn to={classPath(classId, '/bai-giao')}>Về bài đã giao</NavLinkBtn>
      </div>
      {asking && (
        <Confirm title="Hủy bài giao?" yes="Hủy bài giao" busy={cancel.isPending} onNo={() => { setAsking(false); cancel.reset() }}
          onYes={() => cancel.mutate(undefined, { onSuccess: () => setAsking(false) })}
          body="Học viên sẽ không nộp thêm được; bài đã nộp vẫn được giữ."
          error={cancel.isError && <WriteError error={cancel.error} />} />
      )}
    </>
  )
}
