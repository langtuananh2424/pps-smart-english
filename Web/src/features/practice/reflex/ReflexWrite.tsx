import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { ApiError } from '../../../lib/api'
import { createSession, submitWriting } from '../../writing/api'
import { inlineMessage } from '../../writing/errors'
import { area } from '../../writing/Frame'
import { useStoredText, wordCount } from '../../writing/useStoredText'
import { writingKey } from '../ai/answer'
import { pillPrimary } from '../questions/ui'
import { fetchReflex } from './api'

// Bước 1 · Viết của Câu ngắn (D-158): nộp tối đa N lần, máy chủ chấm tự động rồi mới mở bước Nói. Không hiện điểm hay bản sửa (D-147).
export function ReflexWrite({ attemptId, questionId, disabled }: { attemptId: number; questionId: number; disabled?: boolean }) {
  const qc = useQueryClient()
  const key = ['attempt', attemptId, 'reflex', questionId]
  const p = useQuery({ queryKey: key, queryFn: () => fetchReflex(attemptId, questionId), retry: false, refetchInterval: (q) => (q.state.data?.writePending ? 3000 : false) })
  const [text, setText] = useStoredText(writingKey(attemptId, questionId))
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const idem = useRef({ text: '', key: '' })
  const sending = useRef(false)
  const d = p.data
  const left = d ? d.writeMax - d.writeUsed : 0
  const canSend = !!d && !d.writePending && !d.writePassed && left > 0 && !!text.trim() && !disabled && !busy

  const send = async () => {
    if (sending.current) return
    sending.current = true
    setBusy(true)
    setNotice(null)
    const body = text.trim()
    try {
      const { sessionId } = await createSession(questionId, attemptId)
      if (idem.current.text !== body) idem.current = { text: body, key: crypto.randomUUID() }
      await submitWriting(sessionId, body, idem.current.key)
      setText('')
      await qc.invalidateQueries({ queryKey: key })
    } catch (e) {
      setNotice(e instanceof ApiError && e.status === 402 ? 'Bạn đã hết lượt chấm bài viết.' : inlineMessage(e))
      void qc.invalidateQueries({ queryKey: key })
    } finally {
      sending.current = false
      setBusy(false)
    }
  }

  const status = d?.writePending ? 'Cam đang chấm bài, bạn chờ chút nha.' : d?.writePassed ? 'Bài viết đạt, chuyển sang bước nói.' : d && d.writeUsed > 0 ? (left > 0 ? 'Câu này cần sửa và luyện thêm. Hãy viết lại rồi nộp tiếp nhé.' : 'Đã hết lượt viết, chuyển sang bước nói.') : null
  return (
    <section aria-label="Bước viết" className="flex flex-col gap-3">
      <textarea aria-label="Câu trả lời của bạn" className={area} value={text} maxLength={1000} rows={3} placeholder="Nhập câu trả lời…" disabled={disabled || busy || !d || d.writePending} onChange={(e) => setText(e.target.value)} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-[13px] font-extrabold text-ink-secondary">{wordCount(text)} từ · Đã nộp {d?.writeUsed ?? 0}/{d?.writeMax ?? '—'} lần</p>
        <button type="button" disabled={!canSend} onClick={() => void send()} className={pillPrimary}>Nộp câu viết</button>
      </div>
      {status && <p role="status" className="rounded-md bg-muted px-3.5 py-2 text-sm font-bold text-ink">{status}</p>}
      {notice && <p role="alert" className="rounded-md bg-danger-bg p-3 text-sm font-bold text-danger-fg">{notice}</p>}
    </section>
  )
}
