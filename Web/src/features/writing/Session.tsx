import { useQuery } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { fetchSession, requestDraft, sendAnswer, submitWriting } from './api'
import { BlockingError, inlineMessage, QuotaCard } from './errors'
import { area, card, Frame, PromptCard } from './Frame'
import { JobWaiter } from './JobWaiter'
import { WritingResult, type RewriteState } from './WritingResult'
import { ApiError } from '../../lib/api'
import type { JobView, SessionView } from './types'
import { clearStoredText, useStoredText, wordCount } from './useStoredText'

interface Wait { jobId: number; label: string }
const LABEL = { followup: 'Cam đang đọc…', draft: 'Cam đang soạn bản nháp…', grade: 'Đang chấm bài, vài phút nữa có kết quả…' }

// Một phiên viết: phát triển ý → bản nháp → chờ chấm → kết quả (FR-AI-01, FR-AI-02). Server là nguồn sự thật, mỗi bước xong thì nạp lại phiên.
export function Session({ sessionId, onRewrite, rewrite }: { sessionId: number; onRewrite: () => void; rewrite?: RewriteState }) {
  const q = useQuery({ queryKey: ['writing', 'session', sessionId], queryFn: () => fetchSession(sessionId), retry: false, staleTime: 0, gcTime: 0 })
  if (q.isError) return <BlockingError error={q.error} onRetry={() => void q.refetch()} />
  if (!q.data) return <p role="status" className="p-10 text-center font-bold text-ink-secondary">Đang tải…</p>
  return <Steps s={q.data} reload={async () => void (await q.refetch())} onRewrite={onRewrite} rewrite={rewrite} />
}

function Steps({ s, reload, onRewrite, rewrite }: { s: SessionView; reload: () => Promise<void>; onRewrite: () => void; rewrite?: RewriteState }) {
  const id = s.sessionId
  const [view, setView] = useState<'qa' | 'draft'>(s.status === 'READY' && s.draft ? 'draft' : 'qa')
  const [local, setLocal] = useState<Wait | null>(null)
  const [failed, setFailed] = useState<number[]>([]) // job đã lỗi: không chờ lại khi nạp phiên
  const [notice, setNotice] = useState<string | null>(null)
  const [quota, setQuota] = useState<ApiError | null>(null)

  const server: Wait | null = s.pendingJobId
    ? { jobId: s.pendingJobId, label: s.status === 'READY' ? LABEL.draft : LABEL.followup }
    : s.status === 'SUBMITTED' && !s.grade && s.gradeJobId ? { jobId: s.gradeJobId, label: LABEL.grade } : null
  const wait = local ?? (server && !failed.includes(server.jobId) ? server : null)

  const run = async (fn: () => Promise<Wait | null>) => {
    setNotice(null)
    setQuota(null)
    try {
      const w = await fn()
      await reload()
      setLocal(w)
    } catch (e) {
      if (e instanceof ApiError && e.status === 402) setQuota(e)
      else setNotice(inlineMessage(e))
    }
  }

  const done = (job: JobView) => {
    setLocal(null)
    if (job.status === 'FAILED') {
      setFailed((f) => [...f, job.id])
      setNotice(job.quotaRefunded ? 'Chấm bài gặp lỗi, lượt chấm đã được hoàn lại. Bạn có thể viết lại.' : 'Cam gặp lỗi khi xử lý. Hãy thử lại.')
    }
    void reload()
  }

  if (s.status === 'SUBMITTED' && s.grade) return <WritingResult s={s} onRewrite={onRewrite} rewrite={rewrite} />
  if (quota) return <Frame left={<PromptCard question={s.question} />}><QuotaCard error={quota} /></Frame>

  const waiter = wait && <JobWaiter key={wait.jobId} jobId={wait.jobId} label={wait.label} onDone={done} />
  const alert = notice && <p role="alert" className="rounded-md bg-danger-bg p-3 text-sm font-bold text-danger-fg">{notice}</p>

  if (s.status === 'SUBMITTED') {
    return (
      <Frame left={<PromptCard question={s.question} />} actions={failed.length > 0 && <button type="button" onClick={onRewrite} className={pillPrimary}>Viết lại</button>}>
        <section className={card}>{waiter}{alert}</section>
      </Frame>
    )
  }
  if (view === 'draft' && s.draft) {
    return <DraftStep s={s} busy={!!wait} waiter={waiter} alert={alert} onBack={() => setView('qa')} onSubmit={(text, key) => run(async () => {
      const r = await submitWriting(id, text, key)
      clearStoredText(`writing:draft:${id}`)
      return { jobId: r.jobId, label: LABEL.grade }
    })} />
  }
  return <Develop s={s} busy={!!wait} waiter={waiter} alert={alert}
    onAnswer={(text) => run(async () => {
      const r = await sendAnswer(id, text)
      clearStoredText(`writing:answer:${id}`)
      return r.jobId ? { jobId: r.jobId, label: LABEL.followup } : null
    })}
    onDraft={() => {
      if (s.draft) return setView('draft')
      void run(async () => ({ jobId: (await requestDraft(id)).jobId, label: LABEL.draft })).then(() => setView('draft'))
    }} />
}

// Phát triển ý (Figma 10.26 "Làm bài"): đề bên trái, chuỗi hỏi–đáp bên phải.
function Develop({ s, busy, waiter, alert, onAnswer, onDraft }: { s: SessionView; busy: boolean; waiter: React.ReactNode; alert: React.ReactNode; onAnswer: (t: string) => void; onDraft: () => void }) {
  const [text, setText] = useStoredText(`writing:answer:${s.sessionId}`)
  const ready = s.status === 'READY'
  const last = s.turns.at(-1)
  const canAnswer = !ready && !busy && last !== undefined
  return (
    <Frame left={<PromptCard question={s.question} />} actions={ready && (
      <button type="button" disabled={busy} onClick={onDraft} className={`${pillPrimary} w-full md:w-[220px]`}>Xem bài viết →</button>
    )}>
      <section aria-label="Cam hỏi và bạn trả lời" className={card}>
        {s.turns.slice(1).map((t, i) => t.role === 'CAM' ? (
          <div key={i} className="flex items-start gap-3 rounded-md bg-subtle p-3.5">
            <img src={cam} alt="" width={44} height={44} className="shrink-0" />
            <div>
              <p className="text-[13px] font-extrabold leading-[18px] text-brand-strong">CAM · VIẾT DẪN DẮT</p>
              <p className="text-base font-semibold text-ink">{t.text}</p>
            </div>
          </div>
        ) : (
          <p key={i} className="whitespace-pre-wrap rounded-md border-[1.5px] border-line bg-input p-3.5 text-base font-semibold text-ink">{t.text}</p>
        ))}
        {ready && <p className="rounded-md bg-success-bg p-3 text-sm font-bold text-success-fg">Cam đã hiểu ý bạn. Bấm “Xem bài viết” để nhận bản nháp.</p>}
        {waiter}
        {canAnswer && (
          <>
            <textarea aria-label="Câu trả lời của bạn" className={area} value={text} maxLength={5000} onChange={(e) => setText(e.target.value)} placeholder="Viết câu trả lời tại đây…" />
            <p className="text-[13px] font-extrabold text-ink-secondary">{wordCount(text)} từ · Bản nháp</p>
            <button type="button" disabled={!text.trim()} onClick={() => onAnswer(text.trim())} className={`${pillPrimary} self-end`}>Gửi trả lời</button>
          </>
        )}
        {alert}
      </section>
    </Frame>
  )
}

// Xem trước bản nháp (Figma "Xem trước bản nháp"): sửa được, gửi chấm với Idempotency-Key.
function DraftStep({ s, busy, waiter, alert, onBack, onSubmit }: { s: SessionView; busy: boolean; waiter: React.ReactNode; alert: React.ReactNode; onBack: () => void; onSubmit: (t: string, key: string) => void }) {
  const [text, setText] = useStoredText(`writing:draft:${s.sessionId}`, s.draft ?? '')
  const keyRef = useRef({ text: '', key: '' })
  const required = s.question.payload?.requiredWords
  const submit = () => {
    // Cùng nội dung giữ nguyên key (bấm lại sau lỗi mạng); đổi nội dung thì key mới, nếu không backend báo trùng key.
    if (keyRef.current.text !== text) keyRef.current = { text, key: crypto.randomUUID() }
    onSubmit(text, keyRef.current.key)
  }
  const words = wordCount(text)
  return (
    <Frame left={<PromptCard question={s.question} />}>
      <section aria-label="Bản nháp" className={card}>
        <p className="text-[13px] font-extrabold leading-[18px] text-brand-strong">BẢN NHÁP CỦA CAM</p>
        <textarea aria-label="Bản nháp bài viết" className={`${area} min-h-[320px]`} value={text} maxLength={5000} disabled={busy} onChange={(e) => setText(e.target.value)} />
        <p className="text-[13px] font-extrabold text-ink-secondary">{required != null ? `${words}/${required} từ` : `${words} từ`}</p>
        {waiter}
        {alert}
        <button type="button" disabled={busy || !text.trim()} onClick={submit} className={`${pillPrimary} w-full`}>Gửi chấm →</button>
        <button type="button" disabled={busy} onClick={onBack} className={`${pillNeutral} w-full`}>Quay lại chỉnh sửa</button>
      </section>
    </Frame>
  )
}
