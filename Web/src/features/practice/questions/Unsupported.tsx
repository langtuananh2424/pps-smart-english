import { Link } from 'react-router'
import type { QuestionProps } from '../types'
import { pillPrimary } from './ui'

// WRITING/SPEAKING (D-66): làm ở màn riêng, không gửi đáp án. Màn Viết đã có; màn Nói đợt sau (D-83).
export function Unsupported({ question }: QuestionProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-md border-2 border-dashed border-line-dashed bg-subtle px-5 py-6 text-center font-bold text-brand-strong">
      {question.type === 'WRITING' ? (
        <>
          <p>Câu này làm ở màn Viết, không tính điểm ở đây.</p>
          <Link to={`/viet/${question.id}`} className={`${pillPrimary} !h-11 !text-base`}>Làm ở màn Viết</Link>
        </>
      ) : (
        <p>Câu này làm ở màn Viết/Nói (sắp có).</p>
      )}
    </div>
  )
}
