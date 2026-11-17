import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import { chip } from '../lectures/ui'
import { labelOf } from '../exercises/format'
import { fetchAttempt } from './api'
import { useGrammarElements } from './elements'
import { describeAnswer } from './describe'
import { BuildDetail, IdentifyDetail } from './ResultDetails'
import { isAnswerable } from './questions/registry'
import { pillNeutral, pillPrimary } from './questions/ui'
import type { Answers, AttemptResult, Bank, Question, QuestionResult } from './types'

export const POLL_MS = 3000
export const POLL_LIMIT_MS = 120_000
// Giữ số lẻ, tối đa 2 chữ số (D-44).
export const formatScore = (n: number) => String(Math.round(n * 100) / 100)

interface Props {
  bank: Bank
  initial: AttemptResult
  answers?: Answers
  onRetry: () => void
}

// Màn kết quả theo khung Figma 12: tiêu đề có nút quay lại, Cam và điểm ở giữa, thanh thao tác bên dưới.
export function ResultView({ bank, initial, answers, onRetry }: Props) {
  const [gaveUp, setGaveUp] = useState(false)
  const q = useQuery({
    queryKey: ['attempt', initial.id, 'result'],
    queryFn: () => fetchAttempt(initial.id),
    initialData: initial,
    staleTime: Infinity,
    // Còn câu AI đang chấm thì hỏi lại mỗi 3 giây, dừng sau 2 phút.
    refetchInterval: (query) => ((query.state.data?.pendingJobs?.length ?? 0) > 0 && !gaveUp ? POLL_MS : false),
  })
  const r = q.data
  const pending = (r.pendingJobs?.length ?? 0) > 0
  useEffect(() => {
    if (!pending) return
    const t = setTimeout(() => setGaveUp(true), POLL_LIMIT_MS)
    return () => clearTimeout(t)
  }, [pending])

  const elements = useGrammarElements()
  const byQuestion = new Map((r.results ?? []).map((x) => [x.questionId, x]))
  return (
    <div className="min-h-dvh bg-page px-4 py-4 md:px-4">
      <main className="mx-auto flex max-w-[900px] flex-col gap-[18px]">
        <header className="flex items-center gap-3">
          <Link to="/bai-tap" aria-label="Về danh sách bài tập" className="grid size-12 shrink-0 place-items-center rounded-md border-[1.5px] border-line bg-surface text-[28px] font-black leading-5 text-brand-strong">
            ‹
          </Link>
          <div>
            <p className="text-[13px] font-extrabold leading-[18px] text-brand-strong">BÀI TẬP</p>
            <h1 className="text-2xl font-black leading-[30px] text-ink md:text-[30px] md:leading-[38px]">{bank.title}</h1>
          </div>
        </header>

        <section aria-label="Điểm" className="flex flex-col items-center gap-2.5 p-6 text-center">
          <img src={cam} alt="" width={120} height={120} />
          <p className="text-5xl font-black text-brand-strong">
            {r.score != null ? formatScore(r.score) : '—'}
            <span className="text-2xl text-ink-muted">/{r.maxScore ?? '—'}</span>
          </p>
          {pending && !gaveUp && <p role="status" className="text-lg font-black text-ink">Đang chấm câu viết lại, điểm sẽ cập nhật…</p>}
          {pending && gaveUp && (
            <p className="text-base font-semibold text-ink-secondary">
              Chấm chưa xong.{' '}
              <button type="button" onClick={() => { setGaveUp(false); void q.refetch() }} className="font-black text-brand-strong underline">Tải lại</button>
            </p>
          )}
          {r.aiQuotaRequired && (
            <p role="alert" className="rounded-md bg-reward-bg px-4 py-2 text-sm font-bold text-reward-fg">
              Bạn đã hết lượt chấm AI nên một số câu chưa được chấm.
              {r.products && r.products.length > 0 && ` Gói bổ sung: ${r.products.map((p) => p.name).join(', ')}.`}
            </p>
          )}
        </section>

        <ol className="flex flex-col gap-4">
          {bank.questions.map((question, i) => (
            <Row key={question.id} index={i} question={question} result={byQuestion.get(question.id)} mine={answers?.[question.id]} showMine={answers !== undefined} pending={pending} elementName={elements.name} />
          ))}
        </ol>

        <div className="flex flex-wrap justify-center gap-3 pb-4">
          <button type="button" onClick={onRetry} className={`${pillPrimary} w-full md:w-[332px]`}>
            Làm lại
          </button>
          <Link to="/bai-tap" className={`${pillNeutral} w-full md:w-auto`}>
            Về danh sách bài tập
          </Link>
        </div>
      </main>
    </div>
  )
}

function Row({ index, question, result, mine, showMine, pending, elementName }: { index: number; question: Question; result?: QuestionResult; mine?: Answers[number]; showMine: boolean; pending: boolean; elementName: (c: string) => string }) {
  const skipped = !isAnswerable(question.type)
  const grading = !skipped && result?.isCorrect == null && pending
  const badge = skipped ? null : grading ? ['Đang chấm…', 'bg-muted text-ink-secondary'] : result?.isCorrect ? ['Đúng', 'bg-success-bg text-success-fg'] : result?.isCorrect === false ? ['Chưa đúng', 'bg-danger-bg text-danger-fg'] : ['Chưa chấm', 'bg-muted text-ink-secondary']
  const yours = describeAnswer(question, mine, elementName)
  const correct = describeAnswer(question, result?.correctAnswer, elementName)
  // Hai dạng có bố cục kết quả riêng (Figma 415); các dạng còn lại hiện dòng Bài làm / Đáp án đúng.
  const custom = result?.correctAnswer && (question.type === 'GRAMMAR_IDENTIFY' ? IdentifyDetail : question.type === 'GRAMMAR_BUILD' ? BuildDetail : null)
  return (
    <li className="flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6">
      <div className="flex items-center justify-between gap-2">
        <p className="text-[13px] font-extrabold leading-[18px] text-brand-strong">Câu {index + 1} · {question.instruction ?? labelOf(question.type)}</p>
        {badge && <span className={`${chip} ${badge[1]}`}>{badge[0]}</span>}
      </div>
      <p className="text-lg font-black leading-6 text-ink">{question.prompt}</p>
      {skipped ? (
        <p className="text-sm font-semibold text-ink-secondary">Câu này làm ở màn Viết/Nói, không tính điểm ở đây.</p>
      ) : (
        <>
          {custom ? (
            (() => {
              const Detail = custom
              return <Detail question={question} mine={showMine ? mine : undefined} correct={result!.correctAnswer!} />
            })()
          ) : (
            <dl className="flex flex-col gap-1.5 text-sm font-semibold">
              {showMine && <Line label="Bài làm" text={yours ?? 'Chưa làm'} />}
              {correct && <Line label="Đáp án đúng" text={correct} />}
            </dl>
          )}
          <dl className="flex flex-col gap-1.5 text-sm font-semibold">
            {result?.explanation && <Line label="Giải thích" text={result.explanation} />}
            {result?.feedback && <Line label="Nhận xét" text={result.feedback} />}
          </dl>
        </>
      )}
    </li>
  )
}

const Line = ({ label, text }: { label: string; text: string }) => (
  <div className="flex gap-2">
    <dt className="shrink-0 font-extrabold text-ink-secondary">{label}:</dt>
    <dd className="text-ink">{text}</dd>
  </div>
)
