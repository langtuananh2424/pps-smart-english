import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../lib/api'
import { QuestionStem } from '../QuestionStem'
import { option, optionCode, pillNeutral, pillPrimary } from '../questions/ui'
import type { Answer, BankGroup, Question } from '../types'
import { fetchVideoQuiz, submitVideoQuiz } from './api'
import type { VideoGroupState, VideoQuizState, WatchProgress } from './types'
import { WatchPlayer } from './WatchPlayer'

interface Props {
  attemptId: number
  group: BankGroup
  questions: Question[]
  answers: Record<number, Answer | undefined>
  onChange: (questionId: number, v: Answer | undefined) => void
  disabled?: boolean
}

const idsOf = (v: Answer | undefined) => (v?.choiceIds ?? []) as number[]
const pctOf = (watched: number, duration?: number | null) => (duration ? Math.min(100, Math.floor((watched / duration) * 100)) : 0)
const card = 'flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6'
const kicker = 'text-[13px] font-extrabold leading-[18px] text-brand-strong'
const WRONG = '!border-danger !bg-danger-bg'
const RIGHT = '!border-success-fg !bg-success-bg'

// D-159 (Figma 10.32, 11.32): giới thiệu, xem video khóa tua, quiz tối đa 2 lần nộp, chốt lượt hiện đáp án và giải thích.
export function VideoQuizGroup({ attemptId, group, questions, answers, onChange, disabled }: Props) {
  const qc = useQueryClient()
  const key = ['attempt', attemptId, 'video-quiz']
  const state = useQuery({ queryKey: key, queryFn: () => fetchVideoQuiz(attemptId), retry: false })
  const g = state.data?.groups.find((x) => x.groupId === group.id)
  const [live, setLive] = useState<WatchProgress | null>(null)
  const [started, setStarted] = useState(false)
  const [quizOpen, setQuizOpen] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const synced = useRef(-1)

  // Nạp lại câu trả lời của lần nộp cuối khi tải lại trang.
  useEffect(() => {
    if (!g?.answers || synced.current === g.submits) return
    synced.current = g.submits
    g.answers.forEach((a) => onChange(a.questionId, a.answer))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g?.submits])

  if (state.isError && !state.data) return <Problem onRetry={() => void state.refetch()} />
  if (!g) return <p role="status" className="p-4 text-center font-bold text-ink-secondary">Đang tải…</p>

  const watched = g.watched || live?.watched === true
  const percent = pctOf(live?.watchedSec ?? g.watchedSec, live?.durationSec ?? g.durationSec)
  const onProgress = (p: WatchProgress) => {
    setLive(p)
    if (p.watched && !g.watched) void qc.invalidateQueries({ queryKey: key })
  }
  const outcome = (id: number) => g.results?.find((r) => r.questionId === id)
  const complete = questions.every((q) => idsOf(answers[q.id]).length > 0)
  const locked = disabled || busy || g.finalized

  const submit = async () => {
    setBusy(true)
    setError(null)
    try {
      const next = await submitVideoQuiz(attemptId, group.id, questions.map((q) => ({ questionId: q.id, answer: { choiceIds: idsOf(answers[q.id]) } })))
      qc.setQueryData<VideoQuizState>(key, (old) => old && { ...old, groups: old.groups.map((x) => (x.groupId === next.groupId ? next : x)) })
      if (next.finalized) void qc.invalidateQueries({ queryKey: key })
    } catch (e) {
      setError(e instanceof ApiError && e.status < 500 ? e.message : 'Bạn có thể thử lại.')
      setBusy(false)
      if (e instanceof ApiError && e.code === 'video_not_watched') void qc.invalidateQueries({ queryKey: key })
      return
    }
    setBusy(false)
  }

  const toggle = (q: Question, id: number) => {
    const ids = idsOf(answers[q.id])
    const next = q.payload?.multiple === true ? (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]) : [id]
    onChange(q.id, next.length ? { choiceIds: next } : undefined)
  }

  // Màn giới thiệu (Figma 10.32 · Bắt đầu xem): chỉ khi chưa xem gì.
  if (!started && g.watchedSec === 0 && !g.watched && !g.finalized)
    return (
      <section aria-label="Giới thiệu video từ nối" className={card}>
        <p className={kicker}>BÀI TẬP RIÊNG · TRẮC NGHIỆM CUỐI LƯỢT</p>
        <h2 className="text-lg font-black leading-6 text-ink md:text-2xl md:leading-[30px]">{group.title ?? 'Video từ nối'}</h2>
        <p className="text-sm font-semibold text-ink-secondary">Quiz chỉ mở khi đã xem đủ 100% của lượt.</p>
        <Step n="01" title="Xem trọn video" text="Lượt xem chạy một mạch. Điều khiển tạm dừng và tua tới bị khóa." />
        <Step n="02" title="Trả lời câu hỏi" text={`Chọn đủ đáp án trước khi nộp. Mỗi lượt xem có tối đa ${g.maxSubmits} lần nộp.`} />
        <button type="button" disabled={disabled} onClick={() => setStarted(true)} className={`${pillPrimary} self-center`}>
          Bắt đầu xem
        </button>
      </section>
    )

  const total = state.data && { passed: state.data.passedAttempts, required: state.data.requiredAttempts }

  // Màn quiz (Figma 10.32 · Quiz, 11.32 · Sai / Chốt lượt).
  if (watched && quizOpen)
    return (
      <section aria-label="Quiz video từ nối" className={card}>
        <p className={kicker}>{g.finalized ? 'ĐÃ XEM 100%  ·  LƯỢT ĐÃ CHỐT' : `ĐÃ XEM 100%  ·  LẦN NỘP ${g.submits + 1} / ${g.maxSubmits}`}</p>
        <h2 className="text-lg font-black leading-6 text-ink md:text-2xl md:leading-[30px]">Trả lời để tính lượt xem</h2>
        <p className="text-sm font-semibold text-ink-secondary">Chọn đủ đáp án trước khi nộp.</p>
        <ol className="flex flex-col gap-4">
          {questions.map((q, i) => {
            const o = outcome(q.id)
            const mine = idsOf(answers[q.id])
            const right = o?.correctAnswer?.choiceIds ?? []
            return (
              <li key={q.id} aria-label={`Câu quiz ${i + 1}`} className="flex flex-col gap-3">
                <QuestionStem question={q} className="text-base font-black leading-6 text-ink" />
                <div role="group" aria-label="Các lựa chọn" className="flex flex-col gap-3">
                  {(q.choices ?? []).map((c) => {
                    const tone = g.finalized && right.includes(c.id) ? RIGHT : o && !o.correct && mine.includes(c.id) ? WRONG : ''
                    return (
                      <button key={c.id} type="button" role={q.payload?.multiple ? 'checkbox' : 'radio'} aria-checked={mine.includes(c.id)} disabled={locked} onClick={() => toggle(q, c.id)} className={`${option(mine.includes(c.id))} ${tone}`}>
                        <span className={optionCode}>{c.label}</span>
                        {c.contentHtml ? <span dangerouslySetInnerHTML={{ __html: c.contentHtml }} /> : <span>{c.content}</span>}
                      </button>
                    )
                  })}
                </div>
                {g.finalized && o?.explanation && (
                  <p className="rounded-md bg-subtle px-4 py-3 text-sm font-semibold text-ink-secondary"><b className="text-brand-strong">Cam · </b>{o.explanation}</p>
                )}
              </li>
            )
          })}
        </ol>
        {!g.finalized && g.submits > 0 && (
          <div role="status" className="rounded-md bg-danger-bg px-4 py-3">
            <p className="font-black text-danger">Còn {g.maxSubmits - g.submits} lần nộp</p>
            <p className="text-sm font-semibold text-ink-secondary">Lựa chọn sai được đánh dấu đỏ. Đáp án đúng sẽ hiện sau khi chốt lượt.</p>
          </div>
        )}
        {g.finalized && (
          <div role="status" className={`rounded-md px-4 py-3 ${g.passed ? 'bg-success-bg' : 'bg-danger-bg'}`}>
            <p className={`font-black ${g.passed ? 'text-success-fg' : 'text-danger-fg'}`}>{g.passed ? 'Lượt xem đã được tính' : 'Lượt xem này chưa đạt'}</p>
            <p className="text-sm font-semibold text-ink-secondary">Đáp án đúng đã được mở để đối chiếu.</p>
            {state.data && <p className="text-sm font-bold text-ink-secondary">Lượt {state.data.attemptNo} · Đã chốt · Đã đạt {total?.passed}/{total?.required} lượt cần đạt</p>}
          </div>
        )}
        {error && <Problem text={error} />}
        <div className="flex flex-wrap justify-end gap-3">
          <button type="button" onClick={() => setQuizOpen(false)} className={pillNeutral}>Đóng quiz</button>
          {!g.finalized && (
            <button type="button" disabled={locked || !complete} onClick={() => void submit()} className={pillPrimary}>Nộp câu trả lời</button>
          )}
        </div>
      </section>
    )

  // Màn xem (Figma 10.32 · Đang xem, 11.32 · Mở lại quiz đã đóng).
  return (
    <div className="grid items-start gap-3 md:grid-cols-[1fr_320px]">
      <div className="flex flex-col gap-2">
        <WatchPlayer key={group.id} attemptId={attemptId} group={group} startAt={Math.floor(g.watchedSec)} locked={!watched} onProgress={onProgress} />
        <p className={kicker}>{watched ? 'XEM LẠI · ĐIỀU KHIỂN MỞ' : 'ĐANG XEM · KHÓA ĐIỀU KHIỂN'}</p>
      </div>
      <aside aria-label="Lượt xem hiện tại" className={card}>
        <p className={kicker}>LƯỢT XEM HIỆN TẠI</p>
        {watched ? (
          <>
            <p className="text-xl font-black text-ink">Đã xem đủ 100%</p>
            <p className="text-sm font-semibold text-ink-secondary">{g.finalized ? 'Lượt đã chốt.' : 'Cần trả lời câu hỏi để chốt lượt.'}</p>
            <button type="button" onClick={() => setQuizOpen(true)} className={pillPrimary}>{g.finalized ? 'Xem kết quả quiz' : 'Mở quiz'}</button>
          </>
        ) : (
          <>
            <p className="text-xl font-black text-ink">Đang xem · {percent}%</p>
            <div role="progressbar" aria-label="Tiến độ xem" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100} className="h-2 overflow-hidden rounded-full bg-muted">
              <div className="h-full bg-brand" style={{ width: `${percent}%` }} />
            </div>
            <p className="text-sm font-semibold text-ink-secondary">Quiz sẽ mở ở cuối video.</p>
          </>
        )}
        {total && (
          <div className="rounded-md bg-subtle px-3 py-2">
            <p className={kicker}>TỔNG SỐ LƯỢT ĐÃ ĐẠT</p>
            <p className="text-sm font-bold text-ink">{total.passed} / {total.required} lượt · Cần {total.required} lượt đạt</p>
          </div>
        )}
      </aside>
    </div>
  )
}

function Step({ n, title, text }: { n: string; title: string; text: string }) {
  return (
    <div className="rounded-md bg-subtle px-4 py-3">
      <p className="font-black text-ink"><span className="text-brand-strong">{n} · </span>{title}</p>
      <p className="text-sm font-semibold text-ink-secondary">{text}</p>
    </div>
  )
}

// Figma 11.32 · Lỗi tải hoặc nộp.
function Problem({ onRetry, text }: { onRetry?: () => void; text?: string }) {
  return (
    <div role="alert" className="rounded-lg bg-danger-bg p-4">
      <p className="font-black text-danger">Chưa thể tải hoặc nộp câu trả lời</p>
      <p className="text-sm font-semibold text-ink-secondary">{text ?? 'Bạn có thể thử lại.'}</p>
      {onRetry && <button type="button" className="mt-2 font-extrabold underline" onClick={onRetry}>Thử lại</button>}
    </div>
  )
}

export type { VideoGroupState }
