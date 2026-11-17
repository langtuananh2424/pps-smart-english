import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router'
import { ApiError } from '../../lib/api'
import { fetchQuestion, latestSubmission } from './api'
import { BlockingError } from './errors'
import { Outcome } from './Outcome'
import { Practice } from './Practice'

const loading = <p role="status" className="p-10 text-center font-bold text-ink-secondary">Đang tải…</p>
const SHOWN = ['GRADING', 'GRADED', 'UNCLEAR']

// /noi/:questionId — màn tập trung ngoài AppShell (FR-AI-03, D-85).
// Vào trang: bản nộp gần nhất đang chấm / đã có kết quả thì hiện tiếp; chưa có (hoặc bấm Nói lại) thì ghi âm.
export function SpeakingPage() {
  const questionId = Number(useParams().questionId)
  const valid = Number.isInteger(questionId)
  const question = useQuery({ queryKey: ['speaking', 'question', questionId], queryFn: () => fetchQuestion(questionId), retry: false, staleTime: 0, gcTime: 0, enabled: valid })
  const latest = useQuery({ queryKey: ['speaking', 'latest', questionId], queryFn: () => latestSubmission(questionId), retry: false, staleTime: 0, gcTime: 0, enabled: valid })
  const [active, setActive] = useState<{ id: number; jobId: number | null } | null>(null)
  const [redo, setRedo] = useState(false)

  if (!valid) return <BlockingError error={new ApiError(404, 'invalid_id', '')} />
  const failure = question.error ?? latest.error
  if (failure) return <BlockingError error={failure} onRetry={() => { void question.refetch(); void latest.refetch() }} />
  if (!question.data || latest.data === undefined) return loading

  const last = latest.data
  const shown = active ?? (!redo && last && SHOWN.includes(last.status) ? { id: last.id, jobId: null } : null)
  if (shown) return <Outcome key={shown.id} question={question.data} id={shown.id} jobId={shown.jobId} onRedo={() => { setActive(null); setRedo(true) }} />
  return <Practice question={question.data} onSubmitted={(id, jobId) => setActive({ id, jobId })} />
}
