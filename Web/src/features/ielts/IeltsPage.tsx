import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Navigate, useParams } from 'react-router'
import { ApiError } from '../../lib/api'
import { fetchBank, fetchOpenAttempt } from '../practice/api'
import { ErrorCard } from '../practice/StatusCard'
import type { Bank } from '../practice/types'
import { startIeltsAttempt } from './api'
import { ExamSession } from './ExamSession'
import { IeltsResult } from './IeltsResult'
import { ResultView } from '../practice/ResultView'
import { aiStageOf, isIeltsBank } from './parts'

const Loading = () => <p role="status" className="p-10 text-center font-bold text-ink-secondary">Đang tải…</p>

// /ielts/:bankId — khung thi IELTS Reading/Listening (FR-EX-05): vào là làm ngay → kết quả. Chế độ do bộ/bài giao quyết định (D-155). Ngoài AppShell.
export function IeltsPage() {
  const bankId = Number(useParams().bankId)
  const bank = useQuery({ queryKey: ['bank', bankId], queryFn: () => fetchBank(bankId), retry: false, staleTime: Infinity })
  const open = useQuery({ queryKey: ['bank', bankId, 'open-attempt'], queryFn: () => fetchOpenAttempt(bankId), enabled: bank.isSuccess, retry: false, staleTime: 0, gcTime: 0 })
  if (bank.isError) return <ErrorCard error={bank.error} onRetry={() => bank.refetch()} />
  if (open.isError) return <ErrorCard error={open.error} onRetry={() => open.refetch()} />
  if (!bank.data || open.data === undefined) return <Loading />
  if (!isIeltsBank(bank.data)) return <Navigate to={`/bai-tap/${bankId}`} replace />
  return <Runner bank={bank.data} reloadBank={() => bank.refetch()} openAttemptId={open.data?.id ?? null} />
}

function Runner({ bank, reloadBank, openAttemptId }: { bank: Bank; reloadBank: () => Promise<unknown>; openAttemptId: number | null }) {
  const [attemptId, setAttemptId] = useState<number | null>(openAttemptId)
  const [doneId, setDoneId] = useState<number | null>(null)
  const [startError, setStartError] = useState<ApiError | Error | null>(null)
  const starting = useRef(false)

  // D-146: thứ tự hộp từ theo id lượt nên tải lại đề sau khi lượt đã mở.
  const open = async (id: number) => {
    await reloadBank()
    setAttemptId(id)
  }
  const start = async () => {
    starting.current = true
    try {
      await open((await startIeltsAttempt(bank.id)).id)
    } catch (e) {
      // Đang có lượt dở (D-147: mỗi bộ một lượt mở, hai chế độ chung): làm tiếp lượt đó.
      const resume = e instanceof ApiError && e.code === 'attempt_in_progress' ? (e.details as { attemptId?: number } | undefined)?.attemptId : null
      if (resume) await open(resume)
      else setStartError(e as Error)
    } finally {
      starting.current = false
    }
  }

  // Không có lượt dở và chưa nộp: bắt đầu ngay, không hộp chọn chế độ.
  useEffect(() => {
    if (attemptId == null && doneId == null && startError == null && !starting.current) void start()
  })

  if (startError) return <ErrorCard error={startError} onRetry={startError instanceof ApiError && startError.status === 403 ? undefined : () => setStartError(null)} />
  // Viết/Nói: màn kết quả chung có nhận xét từng câu và khóa điểm khi còn đang chấm (D-147).
  if (doneId != null && aiStageOf(bank)) return <ResultView bank={bank} initial={{ id: doneId, bankId: bank.id }} onRetry={() => { setDoneId(null); setAttemptId(null) }} />
  if (doneId != null) return <IeltsResult bank={bank} attemptId={doneId} onRetry={() => { setDoneId(null); setAttemptId(null) }} />
  if (attemptId != null) return <ExamSession key={attemptId} bank={bank} attemptId={attemptId} onDone={setDoneId} />
  return <Loading />
}
