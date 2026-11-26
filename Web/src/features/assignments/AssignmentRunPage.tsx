import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../lib/api'
import { fetchAttempt, fetchBank } from '../practice/api'
import { Player } from '../practice/Player'
import { ResultView } from '../practice/ResultView'
import { ErrorCard } from '../practice/StatusCard'
import type { Answers, AttemptResult } from '../practice/types'
import { useMyAssignment } from './api'
import type { Assignment } from './types'
import { useStart } from './useStart'
import { CancelledCard } from './CancelledCard'
import { detailPath, formatDue, isCancelled } from './view'

const Loading = () => <p role="status" className="p-10 text-center font-bold text-ink-secondary">Đang tải…</p>

// Nghe ép (D-143): xem kết quả lượt vừa nộp; "Làm lại" mở luôn lượt mới.
function ForcedResult({ a, bank, forced }: { a: Assignment; bank: Parameters<typeof ResultView>[0]['bank']; forced: { result: AttemptResult; answers?: Answers } }) {
  const { start, error } = useStart(a)
  return (
    <>
      <ResultView bank={bank} initial={forced.result} answers={forced.answers} onRetry={() => void start()} />
      {error && <p role="alert" className="p-3 text-center text-sm font-bold text-danger">{error}</p>}
    </>
  )
}

// /lop/:classId/bai-giao/:assignmentId/lam-bai/:attemptId — làm bài bộ câu hỏi được giao, dùng lại Player và ResultView (FR-ASG-02).
export function AssignmentRunPage() {
  const p = useParams()
  const classId = Number(p.classId)
  const id = Number(p.assignmentId)
  const attemptId = Number(p.attemptId)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [forced, setForced] = useState<{ attemptId: number; result: AttemptResult; answers?: Answers } | null>(null)
  const list = useMyAssignment(classId, id)
  const a = list.data?.classId === classId ? list.data : undefined // URL sai lớp thì như không tìm thấy
  const bankId = a?.bankId ?? null
  // Tải bộ sau khi lượt đã mở: thứ tự hộp từ theo id lượt (D-146).
  const bank = useQuery({ queryKey: ['assignment-bank', attemptId], queryFn: () => fetchBank(bankId!), enabled: bankId != null, retry: false, staleTime: Infinity })
  const attempt = useQuery({ queryKey: ['attempt', attemptId, 'result'], queryFn: () => fetchAttempt(attemptId), retry: false, staleTime: 0, gcTime: 0 })

  // Bài không thuộc mình hoặc 404 (lượt của người khác) xét trước lỗi tải chung.
  const err = list.error ?? bank.error ?? attempt.error
  if ((list.data && !bankId) || (err instanceof ApiError && err.status === 404)) return <Navigate to="/bai-giao" replace />
  if (err) return <ErrorCard error={err} onRetry={() => void (list.isError ? list.refetch() : bank.isError ? bank.refetch() : attempt.refetch())} />
  if (!a || !bank.data || !attempt.data) return <Loading />
  const back = () => navigate(detailPath(a), { replace: true })
  // Nghe ép làm lại nhiều lần nên xem kết quả ngay tại đây (chỉ của lượt này); còn lại sang màn kết quả bài giao.
  if (forced?.attemptId === attemptId) return <ForcedResult a={a} bank={bank.data} forced={forced} />
  if (attempt.data.submittedAt) return <Navigate to={detailPath(a)} replace />
  if (isCancelled(a)) return <CancelledCard a={a} /> // D-153: lượt dở của bài đã hủy không làm tiếp được
  return (
    <>
      <p className="bg-subtle px-4 py-2 text-center text-sm font-extrabold text-brand-strong">Bài giáo viên giao · Hạn {formatDue(a.dueAt)}</p>
      <Player key={attemptId} bank={bank.data} attemptId={attemptId} onDone={async (result, answers) => {
        await qc.invalidateQueries({ queryKey: ['me', 'assignments'] }) // làm mới trạng thái trước khi về màn bài giao
        if (result.forced) setForced({ attemptId, result, answers })
        else back()
      }} />
    </>
  )
}
