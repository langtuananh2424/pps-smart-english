import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { fetchQuestion } from '../../speaking/api'
import { BlockingError } from '../../speaking/errors'
import { Outcome } from '../../speaking/Outcome'
import { Practice } from '../../speaking/Practice'

// Câu Nói trong lượt: dùng lại màn Nói, bản nộp gắn attemptId; "Nói lại" tạo bản nộp mới.
export function SpeakingFlow({ questionId, attemptId, submissionId, onSubmitted }: { questionId: number; attemptId: number; submissionId?: number; onSubmitted: (id: number) => void }) {
  const q = useQuery({ queryKey: ['speaking', 'question', questionId], queryFn: () => fetchQuestion(questionId), retry: false, staleTime: 0, gcTime: 0 })
  const [jobId, setJobId] = useState<number | null>(null)
  const [redo, setRedo] = useState(false)
  if (q.isError) return <BlockingError error={q.error} onRetry={() => void q.refetch()} />
  if (!q.data) return <p role="status" className="p-10 text-center font-bold text-ink-secondary">Đang tải…</p>
  if (submissionId != null && !redo) return <Outcome key={submissionId} question={q.data} id={submissionId} jobId={jobId} onRedo={() => setRedo(true)} sealed />
  return <Practice question={q.data} attemptId={attemptId} onSubmitted={(id, job) => { setJobId(job); setRedo(false); onSubmitted(id) }} />
}
