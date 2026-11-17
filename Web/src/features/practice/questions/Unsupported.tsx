import { Link } from 'react-router'
import type { QuestionProps } from '../types'
import { pillPrimary } from './ui'

// WRITING/SPEAKING (D-66): làm ở màn riêng, không gửi đáp án.
export function Unsupported({ question }: QuestionProps) {
  const writing = question.type === 'WRITING'
  const screen = writing ? 'Viết' : 'Nói'
  return (
    <div className="flex flex-col items-center gap-3 rounded-md border-2 border-dashed border-line-dashed bg-subtle px-5 py-6 text-center font-bold text-brand-strong">
      <p>Câu này làm ở màn {screen}, không tính điểm ở đây.</p>
      <Link to={`/${writing ? 'viet' : 'noi'}/${question.id}`} className={`${pillPrimary} !h-11 !text-base`}>Làm ở màn {screen}</Link>
    </div>
  )
}
