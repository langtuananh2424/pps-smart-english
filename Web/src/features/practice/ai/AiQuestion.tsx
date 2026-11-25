import { useState } from 'react'
import { ApiError } from '../../../lib/api'
import { pillNeutral, pillPrimary } from '../questions/ui'
import { createSession } from '../../writing/api'
import { inlineMessage, QuotaCard } from '../../writing/errors'
import { Session } from '../../writing/Session'
import type { AiSession, Question } from '../types'
import { SpeakingFlow } from './SpeakingFlow'

interface Props {
  question: Question
  attemptId: number
  session?: AiSession // phiên Viết / bản nộp Nói mới nhất của câu trong lượt, từ máy chủ
  onRef: (id: number, status: AiSession['status']) => void
  onClose: () => void // đóng màn Viết/Nói: hỏi lại trạng thái thật
  loaded: boolean // chưa biết lượt đã có phiên nào thì chưa cho mở, tránh tạo phiên trống đè phiên đã chấm
  disabled?: boolean
}

const STATE_TEXT: Record<AiSession['status'], string> = {
  NOT_SUBMITTED: 'Đã mở bài nhưng chưa nộp chấm. Chưa nộp thì câu này tính 0 điểm.',
  GRADING: 'Đã nộp, AI đang chấm. Điểm được cộng khi chấm xong.',
  GRADED: 'AI đã chấm xong. Điểm và nhận xét hiện sau khi bạn nộp bài.',
  FAILED: 'Lần chấm trước chưa có điểm. Bạn có thể làm lại.',
}

// Câu Viết/Nói trong lượt (D-66, D-145): mở màn Viết/Nói sẵn có phủ lên bài, phiên gắn attemptId. Không hiện điểm tại đây.
export function AiQuestion({ question, attemptId, session, onRef, onClose, loaded, disabled }: Props) {
  const refId = session?.id
  const writing = question.type === 'WRITING'
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  // Viết lại cũng đi đường này: phiên mới thành phiên được tính.
  const startWriting = async () => {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      onRef((await createSession(question.id, attemptId)).sessionId, 'NOT_SUBMITTED')
      setOpen(true)
    } catch (e) {
      if (e instanceof ApiError && e.status === 402) setError(e)
      else setNotice(inlineMessage(e))
    } finally {
      setBusy(false)
    }
  }
  const openIt = () => (writing && refId == null ? void startWriting() : setOpen(true))
  const label = writing ? (refId == null ? 'Bắt đầu viết' : 'Tiếp tục bài viết') : refId == null ? 'Bắt đầu nói' : 'Xem bài nói'

  return (
    <>
      <div className="flex flex-col items-start gap-3 rounded-md border-2 border-dashed border-line-dashed bg-subtle px-5 py-5 font-bold text-brand-strong">
        <p>{session ? STATE_TEXT[session.status] : loaded ? 'Câu này AI chấm, làm ngay trong lượt. Chưa làm thì tính 0 điểm.' : 'Đang kiểm tra bài đã làm…'}</p>
        <button type="button" disabled={disabled || busy || !loaded} onClick={openIt} className={`${pillPrimary} !h-11 !text-base`}>{busy ? 'Đang tạo…' : label}</button>
        {notice && <p role="alert" className="text-sm font-bold text-danger-fg">{notice}</p>}
      </div>
      {error && <div className="mt-3"><QuotaCard error={error} /></div>}
      {open && (
        <div role="dialog" aria-modal="true" aria-label={writing ? 'Viết tự luận' : 'Luyện nói'} className="fixed inset-0 z-40 overflow-y-auto bg-page">
          <button type="button" onClick={() => { setOpen(false); onClose() }} className={`${pillNeutral} fixed right-4 top-3 z-50 !h-11 !text-sm md:right-7`}>Về bài làm</button>
          {writing && refId != null
            ? <Session key={refId} sessionId={refId} onRewrite={() => void startWriting()} sealed rewrite={{ busy, error: notice }} />
            : <SpeakingFlow questionId={question.id} attemptId={attemptId} submissionId={refId} onSubmitted={(id) => onRef(id, 'GRADING')} />}
        </div>
      )}
    </>
  )
}
