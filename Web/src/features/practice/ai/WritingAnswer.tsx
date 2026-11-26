import { useQuery } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { ApiError } from '../../../lib/api'
import { createSession, fetchSession, submitWriting } from '../../writing/api'
import { inlineMessage } from '../../writing/errors'
import { area } from '../../writing/Frame'
import { useStoredText, wordCount } from '../../writing/useStoredText'
import { pillNeutral } from '../questions/ui'
import { AnswerCard } from './AnswerCard'
import { isSent, usePublishSend, writingKey, type AnswerProps } from './answer'
import { QuotaNotice, useQuotaGate } from './QuotaGate'
import { RedoDialog } from './RedoDialog'

// Câu Viết trong lượt (D-66, D-145): viết ngay trong thẻ câu, "Gửi chấm" tạo phiên gắn attemptId rồi nộp. Không hiện điểm, bài chấm xong mở sau khi nộp cả bộ.
export function WritingAnswer({ question, attemptId, session, loaded, disabled, onRef, onSend, onSkip }: AnswerProps) {
  const [text, setText] = useStoredText(writingKey(attemptId, question.id))
  const [editing, setEditing] = useState(false) // đang viết lại bài đã gửi
  const [redo, setRedo] = useState(false)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const gate = useQuotaGate()
  const open = useRef<number | null>(null) // phiên đã tạo mà chưa nộp được: gửi lại thì dùng tiếp, không tạo thêm
  const key = useRef({ id: 0, text: '', key: '' })
  const sending = useRef(false) // chặn bấm hai lần trước khi busy kịp vẽ lại
  const sent = isSent(session) && !editing
  // Đổi máy / mở lại sau khi xóa bộ nhớ trình duyệt: bài đã gửi lấy từ máy chủ.
  const saved = useQuery({ queryKey: ['writing', 'session', session?.id], queryFn: () => fetchSession(session!.id), enabled: sent && !text.trim(), retry: false })
  const shown = text || (sent ? saved.data?.finalText ?? '' : '')
  const required = question.payload?.requiredWords

  const send = async () => {
    if (sending.current) return
    sending.current = true
    const body = text.trim()
    setBusy(true)
    setNotice(null)
    try {
      const id = open.current ?? (session?.status === 'NOT_SUBMITTED' ? session.id : (await createSession(question.id, attemptId)).sessionId)
      open.current = id
      // Cùng phiên và cùng nội dung giữ nguyên key (gửi lại sau lỗi mạng không trừ lượt hai lần); phiên mới hoặc đổi nội dung thì key mới.
      if (key.current.id !== id || key.current.text !== body) key.current = { id, text: body, key: crypto.randomUUID() }
      await submitWriting(id, body, key.current.key)
      open.current = null
      setEditing(false)
      onRef(id, 'GRADING')
    } catch (e) {
      if (e instanceof ApiError && e.status === 402) gate.hit(e)
      else setNotice(inlineMessage(e))
    } finally {
      sending.current = false
      setBusy(false)
    }
  }
  usePublishSend(onSend, sent ? null : loaded && !busy && !gate.exhausted && !!text.trim(), () => void send())

  return (
    <>
      {gate.exhausted && !sent && <QuotaNotice gate={gate} onSkip={onSkip} />}
      {(!gate.exhausted || sent || !!text.trim()) && (
        <AnswerCard title="Bài viết của bạn" locked={sent}>
          <textarea
            aria-label="Nội dung bài viết" className={`${area} read-only:bg-muted`} value={shown} maxLength={5000} readOnly={sent}
            disabled={disabled || busy} placeholder="Viết bài của bạn tại đây…" onChange={(e) => setText(e.target.value)}
          />
          <p className="text-[13px] font-extrabold text-ink-secondary">{required != null ? `${wordCount(shown)}/${required} từ` : `${wordCount(shown)} từ`}</p>
          {sent && <button type="button" disabled={disabled} onClick={() => setRedo(true)} className={`${pillNeutral} self-start`}>Viết lại</button>}
          {notice && <p role="alert" className="rounded-md bg-danger-bg p-3 text-sm font-bold text-danger-fg">{notice}</p>}
        </AnswerCard>
      )}
      {redo && <RedoDialog title="Viết lại câu này?" confirm="Viết lại" body="Bài mới sẽ thay bài cũ, chỉ bài mới nhất được tính điểm." onCancel={() => setRedo(false)} onConfirm={() => { setText(shown); setEditing(true); setRedo(false) }} />}
    </>
  )
}
