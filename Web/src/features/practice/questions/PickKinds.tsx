import { PickSelect, pickAnswered, pickKey, pickOptions } from '../layout/controls'
import type { QuestionProps } from '../types'
import { TrueFalse } from './TrueFalse'

const withOptions = (q: QuestionProps['question'], options: string[]) => ({ ...q, payload: { ...q.payload, options } })

// Đúng/Sai/Không có thông tin và Có/Không/Không có thông tin dùng lại thẻ chọn một của TRUE_FALSE; đáp án {value}.
export const Tfng = (p: QuestionProps) => <TrueFalse {...p} question={withOptions(p.question, ['TRUE', 'FALSE', 'NOT_GIVEN'])} />
export const Ynng = (p: QuestionProps) => <TrueFalse {...p} question={withOptions(p.question, ['YES', 'NO', 'NOT_GIVEN'])} />
export { pickAnswered }

// Câu chọn một mục từ danh sách chung của nhóm (heading, đoạn, đặc điểm, đuôi câu, điểm trên bản đồ, từ trong hộp).
export function MatchPick({ question, value, onChange, disabled, layout }: QuestionProps) {
  const options = pickOptions(layout)
  const key = pickKey(question)
  if (options.length === 0) return <p className="text-sm font-semibold text-ink-secondary">Thiếu danh sách lựa chọn của nhóm.</p>
  return (
    <PickSelect label="Chọn đáp án" value={value?.[key] as string | undefined} options={options} disabled={disabled} onChange={(v) => onChange(v ? { [key]: v } : undefined)} />
  )
}
