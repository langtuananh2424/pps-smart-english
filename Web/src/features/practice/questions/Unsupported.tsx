import type { QuestionProps } from '../types'

// Viết/Nói làm trong lượt (D-145), không có renderer ở khung này nên chỉ báo trung tính, không dẫn link.
export function Unsupported(_: QuestionProps) {
  return (
    <p className="rounded-md border-2 border-dashed border-line-dashed bg-subtle px-5 py-6 text-center font-bold text-brand-strong">
      Dạng câu này chưa hỗ trợ ở màn này
    </p>
  )
}
