import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../../lib/api'
import { fetchBank, fetchOpenAttempt, startAttempt } from './api'
import { Intro } from './Intro'
import { Player } from './Player'
import { ResultView } from './ResultView'
import { ErrorCard } from './StatusCard'
import type { Answers, AttemptResult, Bank } from './types'

const Loading = () => <p role="status" className="p-10 text-center font-bold text-ink-secondary">Đang tải…</p>

// /bai-tap/:bankId — màn tập trung ngoài AppShell: giới thiệu → làm bài → kết quả (FR-EX-01..06).
export function PracticePage() {
  const bankId = Number(useParams().bankId)
  // shortcut: bài kiểm tra khóa chỉ nhận biết qua ?khoa do link ở trang khóa gắn; lượt dở mở từ nơi khác thì mất và rơi về ResultView — nâng cấp khi backend trả courseId và purpose.
  const courseId = useSearchParams()[0].get('khoa')
  const bank = useQuery({ queryKey: ['bank', bankId], queryFn: () => fetchBank(bankId), retry: false, staleTime: Infinity })
  const open = useQuery({ queryKey: ['bank', bankId, 'open-attempt'], queryFn: () => fetchOpenAttempt(bankId), enabled: bank.isSuccess, retry: false, staleTime: 0, gcTime: 0 })

  if (bank.isError) return <ErrorCard error={bank.error} onRetry={() => bank.refetch()} />
  if (open.isError) return <ErrorCard error={open.error} onRetry={() => open.refetch()} />
  if (!bank.data || open.data === undefined) return <Loading />
  return <Runner bank={bank.data} reloadBank={() => bank.refetch()} openAttemptId={open.data?.id ?? null} courseId={courseId} />
}

function Runner({ bank, reloadBank, openAttemptId, courseId }: { bank: Bank; reloadBank: () => Promise<unknown>; openAttemptId: number | null; courseId: string | null }) {
  const navigate = useNavigate()
  const [attemptId, setAttemptId] = useState<number | null>(openAttemptId)
  const [done, setDone] = useState<{ result: AttemptResult; answers?: Answers } | null>(null)
  const [starting, setStarting] = useState(false)
  const [startError, setStartError] = useState<ApiError | Error | null>(null)

  // D-146: thứ tự hộp từ theo id lượt; bộ tải trước khi có lượt mang seed 0 nên tải lại sau khi lượt đã mở rồi mới hiện Player.
  const open = async (id: number) => {
    await reloadBank()
    setAttemptId(id)
  }

  const start = async () => {
    setStarting(true)
    setStartError(null)
    try {
      await open((await startAttempt(bank.id)).id)
    } catch (e) {
      // Đang có lượt dở (nơi khác, hoặc bấm Bắt đầu hai lần): làm tiếp lượt đó thay vì báo lỗi (D-147: mỗi bộ một lượt mở).
      const resume = e instanceof ApiError && (e.code === 'exam_in_progress' || e.code === 'attempt_in_progress') ? (e.details as { attemptId?: number } | undefined)?.attemptId : null
      if (resume) await open(resume)
      else setStartError(e as Error)
    } finally {
      setStarting(false)
    }
  }

  // Khóa/hết lượt thi là lỗi chặn; lỗi khác (mạng, 429) chỉ báo ngay trên nút Bắt đầu.
  if (startError instanceof ApiError && startError.status === 403) return <ErrorCard error={startError} />
  if (startError instanceof ApiError && startError.code === 'exam_attempts_exhausted') return <ErrorCard error={startError} />
  if (done) return <ResultView bank={bank} initial={done.result} answers={done.answers} onRetry={() => { setDone(null); setAttemptId(null) }} />
  if (attemptId != null) return <Player key={attemptId} bank={bank} attemptId={attemptId} onDone={(result, answers) =>
    // Nghe ép có kết quả riêng (D-142): không chuyển sang màn kiểm tra khóa.
    courseId && !result.forced ? navigate(`/bai-tap/khoa/${encodeURIComponent(courseId)}/kiem-tra/${result.id}/ket-qua`, { replace: true }) : setDone({ result, answers })
  } />
  return (
    <div className="min-h-dvh bg-page px-4">
      <Intro bank={bank} starting={starting} error={startError ? startError.message : null} onStart={() => void start()} />
    </div>
  )
}
