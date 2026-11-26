import { useQuery } from '@tanstack/react-query'
import { Navigate, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../lib/api'
import { fetchAttempt, fetchBank } from './api'
import { ResultView } from './ResultView'
import { ErrorCard } from './StatusCard'

const Loading = () => <p role="status" className="p-10 text-center font-bold text-ink-secondary">Đang tải…</p>

// /bai-tap/:bankId/ket-qua/:attemptId — mở kết quả một lượt theo mã (đích của thông báo EXERCISE_GRADED, D-149).
// Lượt của người khác hoặc không tồn tại: máy chủ trả 404 attempt_not_found.
export function AttemptResultPage() {
  const p = useParams()
  const bankId = Number(p.bankId)
  const attemptId = Number(p.attemptId)
  const navigate = useNavigate()
  const attempt = useQuery({ queryKey: ['attempt', attemptId, 'result'], queryFn: () => fetchAttempt(attemptId), retry: false })
  const bank = useQuery({ queryKey: ['bank', bankId], queryFn: () => fetchBank(bankId), retry: false, staleTime: Infinity })

  // D-152: lượt Luyện thêm gắn bộ neo không mở được như bộ thường (thông báo EXERCISE_GRADED): chuyển sang màn riêng.
  if (attempt.data && bank.error instanceof ApiError && bank.error.code === 'bank_not_found') return <Navigate to={`/bai-tap/luyen-them/${attemptId}`} replace />
  const err = attempt.error ?? bank.error
  if (err) return <ErrorCard error={err} onRetry={() => void (attempt.isError ? attempt.refetch() : bank.refetch())} />
  if (!attempt.data || !bank.data) return <Loading />
  // Mã bộ trong đường dẫn lệch lượt: coi như không có lượt này.
  if (attempt.data.bankId !== bankId) return <ErrorCard error={new ApiError(404, 'attempt_not_found', 'Không tìm thấy lượt làm bài')} />
  // Lượt chưa nộp chưa có kết quả: về màn làm bài để làm tiếp.
  if (!attempt.data.submittedAt) return <Navigate to={`/bai-tap/${bankId}`} replace />
  return <ResultView bank={bank.data} initial={attempt.data} onRetry={() => navigate(`/bai-tap/${bankId}`)} />
}
