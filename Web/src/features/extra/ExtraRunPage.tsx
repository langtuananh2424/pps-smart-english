import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { fetchAttempt } from '../practice/api'
import { Player } from '../practice/Player'
import { ResultView } from '../practice/ResultView'
import { ErrorCard } from '../practice/StatusCard'
import type { Answers, AttemptResult } from '../practice/types'
import { fetchExtraBank } from './api'

const Loading = () => <p role="status" className="p-10 text-center font-bold text-ink-secondary">Đang tải…</p>

// /bai-tap/luyen-them/:attemptId — làm bài và xem kết quả lượt Luyện thêm (D-152), dùng lại Player và ResultView của bộ thường.
export function ExtraRunPage() {
  const attemptId = Number(useParams().attemptId)
  const navigate = useNavigate()
  const [done, setDone] = useState<{ result: AttemptResult; answers?: Answers } | null>(null)
  const bank = useQuery({ queryKey: ['extra-attempt', attemptId], queryFn: () => fetchExtraBank(attemptId), retry: false, staleTime: Infinity })
  const attempt = useQuery({ queryKey: ['attempt', attemptId, 'result'], queryFn: () => fetchAttempt(attemptId), retry: false, staleTime: 0, gcTime: 0 })

  const err = bank.error ?? attempt.error
  if (err) return <ErrorCard error={err} onRetry={() => void (bank.isError ? bank.refetch() : attempt.refetch())} />
  if (!bank.data || !attempt.data) return <Loading />
  const back = () => navigate('/bai-tap')
  if (done) return <ResultView bank={bank.data} initial={done.result} answers={done.answers} onRetry={back} />
  if (attempt.data.submittedAt) return <ResultView bank={bank.data} initial={attempt.data} onRetry={back} />
  return <Player key={attemptId} bank={bank.data} attemptId={attemptId} onDone={(result, answers) => setDone({ result, answers })} />
}
