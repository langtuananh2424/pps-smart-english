import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams, useSearchParams } from 'react-router'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { Link } from 'react-router'
import { useAssignmentCancelled } from '../assignments/api'
import { createSession, latestSession } from './api'
import { ApiError } from '../../lib/api'
import { BlockingError, inlineMessage, inOpenAttempt } from './errors'
import { Session } from './Session'

// /viet/:questionId — màn tập trung ngoài AppShell (FR-AI-01, FR-AI-02, D-84).
// Vào trang: có phiên dở thì tiếp, phiên gần nhất đã nộp thì xem kết quả, chưa có thì màn bắt đầu. Chỉ tạo phiên khi bấm.
export function WritingPage() {
  const questionId = Number(useParams().questionId)
  const assignmentId = Number(useSearchParams()[0].get('assignmentId')) || undefined
  const latest = useQuery({ queryKey: ['writing', 'latest', questionId, assignmentId], queryFn: () => latestSession(questionId, assignmentId), retry: false, staleTime: 0, gcTime: 0 })
  const cancelled = useAssignmentCancelled(assignmentId)
  const [started, setStarted] = useState<number | null>(null)
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const start = async () => {
    setStarting(true)
    setError(null)
    try {
      setStarted((await createSession(questionId, undefined, assignmentId)).sessionId)
    } catch (e) {
      setError(e)
    } finally {
      setStarting(false)
    }
  }

  if (!Number.isInteger(questionId)) return <BlockingError error={new ApiError(404, 'invalid_id', '')} />
  if (latest.isError) return <BlockingError error={latest.error} onRetry={() => void latest.refetch()} />
  if (latest.data === undefined) return <p role="status" className="p-10 text-center font-bold text-ink-secondary">Đang tải…</p>
  const sessionId = started ?? latest.data?.sessionId ?? null
  // Hết lượt hoặc câu bị khóa lúc tạo phiên đầu là lỗi chặn; lỗi tạm thời, hay lỗi khi viết lại (đã có bài hiện trên màn), báo tại chỗ.
  const blocking = error as { status?: number } | null
  if (sessionId == null && blocking && (blocking.status === 402 || blocking.status === 403 || blocking.status === 404 || inOpenAttempt(error))) return <BlockingError error={error} />

  if (sessionId != null) return <Session key={sessionId} sessionId={sessionId} onRewrite={() => void start()} rewrite={{ busy: starting, error: error != null ? inlineMessage(error) : null, cancelled }} />
  return (
    <div className="min-h-dvh bg-page px-4">
      <section className="mx-auto mt-10 flex max-w-lg flex-col items-center gap-4 rounded-lg border border-line-subtle bg-surface p-6 text-center shadow-edge-card sm:p-8">
        <img src={cam} alt="" width={96} height={96} />
        <h1 className="text-2xl font-black leading-[30px] text-ink">Viết phát triển ý</h1>
        <p className="font-semibold text-ink-secondary">Cam sẽ hỏi vài câu để bạn phát triển ý, rồi soạn bản nháp cho bạn chỉnh sửa. Chỉ khi bấm “Gửi chấm” mới tính một lượt chấm.</p>
        {error != null && <p role="alert" className="text-sm font-bold text-danger">{inlineMessage(error)}</p>}
        <div className="mt-2 flex flex-wrap justify-center gap-3">
          <button type="button" onClick={() => void start()} disabled={starting} className={pillPrimary}>{starting ? 'Đang tạo…' : 'Bắt đầu viết'}</button>
          <Link to="/bai-tap" className={pillNeutral}>Về danh sách</Link>
        </div>
      </section>
    </div>
  )
}
