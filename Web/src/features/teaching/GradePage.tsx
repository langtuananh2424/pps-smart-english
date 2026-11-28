import { useState } from 'react'
import { PageHead, Section } from '../admin/ui'
import { useAssignmentProgress, useClassAssignments, useClassDetail, useGrades } from './api'
import { GradeForm } from './GradeForm'
import { GradeHistory } from './GradeHistory'
import { classPath, KICKER, score } from './format'
import { useSubmission } from './submissionApi'
import { SubmissionView } from './SubmissionView'
import { Gate, NavLinkBtn, useNum } from './ui'

// #61, FR-ASG-03, D-141: chấm bài. Viết/Nói xem bài nộp; bộ câu chưa có API xem câu trả lời (shortcut: chỉ hiện điểm).
export function GradePage() {
  const classId = useNum('classId')
  const id = useNum('assignmentId')
  const attemptId = useNum('attemptId')
  const list = useClassAssignments(classId)
  const rows = useAssignmentProgress(classId, id)
  const grades = useGrades(classId, id, attemptId)
  const detail = useClassDetail(classId)
  const [cancelled, setCancelled] = useState(false)
  const a = list.data?.find((x) => x.id === id)
  const row = rows.data?.find((r) => r.attemptId === attemptId)
  const ai = a?.questionType != null
  const sub = useSubmission(classId, id, attemptId, ai)
  const max = row?.maxScore ?? null
  return (
    <>
      <PageHead kicker={KICKER} title="Chấm bài và nhận xét" sub={row ? `Bài của ${row.displayName}. Điểm cuối là điểm của bản sửa mới nhất.` : 'Điểm cuối là điểm của bản sửa mới nhất.'} />
      <div className="grid gap-4 lg:grid-cols-[1fr_360px] lg:items-start">
        <div className="flex min-w-0 flex-col gap-4">
          {ai ? <Gate q={sub}>{(s) => <SubmissionView s={s} />}</Gate> : (
            <Section title="Bài làm của học viên">
              <p className="font-semibold text-ink-secondary">Chưa xem được từng câu trả lời ở đây.</p>
              <p className="font-semibold text-ink">Điểm cuối: {score(row?.finalScore ?? null, max)}</p>
            </Section>
          )}
        </div>
        <div className="flex min-w-0 flex-col gap-4">
          <section className="rounded-lg border border-line-brand bg-subtle p-5">
            <h2 className="text-lg font-black text-ink">Điểm Cam chấm · {score(row?.aiScore ?? null, max)}</h2>
            <p className="font-semibold text-ink-secondary">Điểm của Cam được lưu riêng. Điểm cuối: {score(row?.finalScore ?? null, max)}</p>
          </section>
          {row?.forced ? (
            <Section title="Chấm của giáo viên"><p role="status" className="font-semibold text-ink-secondary">Bài Nghe ép không có điểm để sửa</p></Section>
          ) : a && <GradeForm classId={classId} id={id} attemptId={attemptId} max={max} speaking={a.questionType === 'SPEAKING'} readOnly={cancelled || a.status === 'CANCELLED'} onCancelled={() => setCancelled(true)} />}
        </div>
      </div>
      <Gate q={grades}>{(g) => <GradeHistory grades={g} staff={detail.data?.staff ?? []} row={row} />}</Gate>
      <div><NavLinkBtn to={classPath(classId, `/bai-giao/${id}`)}>Về tiến độ nộp bài</NavLinkBtn></div>
    </>
  )
}
