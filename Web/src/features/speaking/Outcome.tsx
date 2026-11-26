import { useQuery } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import type { JobView } from '../../lib/sse'
import { CANCELLED_NOTE } from '../assignments/view'
import { pillPrimary } from '../practice/questions/ui'
import { card, Frame } from '../writing/Frame'
import { JobWaiter } from '../writing/JobWaiter'
import { fetchSubmission } from './api'
import { BlockingError, UNCLEAR } from './errors'
import { Prompt } from './Prompt'
import { SpeakingResultView } from './SpeakingResult'
import type { SpeakingQuestion } from './types'

const POLL_MS = 5000
const GRADING_TEXT = 'Đang chấm bài nói, vài phút nữa có kết quả…'

// Một bản nộp đã có: chờ chấm (SSE khi biết jobId, đồng thời hỏi lại định kỳ), rồi hiện kết quả / không rõ / lỗi.
export function Outcome({ question, id, jobId, onRedo, cancelled }: { question: SpeakingQuestion; id: number; jobId: number | null; onRedo: () => void; cancelled?: boolean }) {
  const [waiting, setWaiting] = useState(jobId != null)
  const [failed, setFailed] = useState<JobView | null>(null)
  const q = useQuery({
    queryKey: ['speaking', 'submission', id], queryFn: () => fetchSubmission(id), retry: false, staleTime: 0, gcTime: 0,
    refetchInterval: (query) => (query.state.data?.status === 'GRADING' ? POLL_MS : false),
  })
  if (q.isError) return <BlockingError error={q.error} onRetry={() => void q.refetch()} />
  if (!q.data) return <p role="status" className="p-10 text-center font-bold text-ink-secondary">Đang tải…</p>
  const s = q.data

  if (s.status === 'GRADED') return <SpeakingResultView question={question} sub={s} onRedo={onRedo} cancelled={cancelled} />

  let body: ReactNode
  if (s.status === 'GRADING' || s.status === 'AWAITING_UPLOAD') {
    body = waiting && jobId != null
      ? <JobWaiter jobId={jobId} label={GRADING_TEXT} onDone={(job) => { setWaiting(false); if (job.status === 'FAILED') setFailed(job); void q.refetch() }} />
      : <p role="status" className="rounded-md bg-subtle p-4 text-base font-extrabold text-brand-ink">{GRADING_TEXT}</p>
  } else if (s.status === 'UNCLEAR') {
    const why = s.result?.reason ? UNCLEAR[s.result.reason] : 'Hệ thống không chấm được bản ghi này.'
    body = (
      <div role="note" className="flex flex-col gap-2 rounded-md bg-reward-bg p-4 text-left text-reward-fg">
        <p className="text-base font-black">Chưa chấm được bản ghi</p>
        <p className="text-sm font-bold">{why} Hãy nói rõ và đủ dài hơn rồi thử lại.</p>
        <p className="text-sm font-extrabold">Không trừ lượt chấm.</p>
      </div>
    )
  } else {
    const msg = s.status === 'EXPIRED'
      ? 'Phiên tải lên đã hết hạn. Hãy nói lại.'
      : failed?.quotaRefunded ? 'Chấm bài gặp lỗi, lượt chấm đã được hoàn lại. Bạn có thể nói lại.' : 'Chấm bài gặp lỗi. Bạn có thể nói lại.'
    body = <p role="alert" className="rounded-md bg-danger-bg p-4 text-sm font-bold text-danger-fg">{msg}</p>
  }
  const redo = s.status === 'GRADING' || s.status === 'AWAITING_UPLOAD' ? undefined : cancelled ? (
    <p role="status" className="font-bold text-ink-secondary">{CANCELLED_NOTE}</p>
  ) : (
    <button type="button" onClick={onRedo} className={`${pillPrimary} w-full md:w-[220px]`}>Nói lại</button>
  )
  return (
    <Frame title="Luyện nói" actions={redo}>
      <Prompt question={question}>
        <section aria-label="Kết quả" className={`${card} items-center text-center`}>
          <img src={cam} alt="" width={72} height={72} />
          <div className="w-full">{body}</div>
        </section>
      </Prompt>
    </Frame>
  )
}
