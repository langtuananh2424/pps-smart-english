import { useGrammarElements } from '../practice/elements'
import { ResultBreakdown } from '../practice/ResultBreakdown'
import { Row } from '../practice/ResultView'
import type { Answers } from '../practice/types'
import type { AttemptReview } from './reviewApi'

// D-174: từng câu của lượt nộp, chỉ đọc. Dùng lại Row của màn kết quả học viên (đề, bài làm dạng chữ, đáp án đúng, giải thích),
// shortcut: bài làm hiện dạng chữ gọn như màn kết quả, không dựng lại component làm bài từng dạng; nâng cấp nếu cần xem đúng bố cục.
export function AttemptReviewView({ review }: { review: AttemptReview }) {
  const elements = useGrammarElements()
  const { attempt, bank, answers } = review
  if (attempt.results == null) {
    return <p role="status" className="font-semibold text-ink-secondary">Chưa có kết quả từng câu (bài Nghe ép hoặc còn câu đang chấm).</p>
  }
  const mine: Answers = Object.fromEntries(answers.map((a) => [a.questionId, a.answer ?? undefined]))
  const credit = new Map(answers.map((a) => [a.questionId, a.credit]))
  const byQuestion = new Map(attempt.results.map((r) => [r.questionId, r]))
  return (
    <div className="flex flex-col gap-4">
      <ResultBreakdown breakdown={attempt.breakdown} bank={bank} elementName={elements.name} />
      <ol className="flex flex-col gap-4">
        {bank.questions.map((q, i) => (
          <Row key={q.id} index={i} question={q} result={byQuestion.get(q.id)} mine={mine[q.id]} showMine mineLabel="Bài làm của học viên" credit={credit.get(q.id)} pending={false}
            elementName={elements.name} layout={bank.groups?.find((g) => g.id === q.groupId)?.layout} />
        ))}
      </ol>
    </div>
  )
}
