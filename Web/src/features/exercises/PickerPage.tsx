import { Link } from 'react-router'
import { CoursePicker } from '../lectures/CoursePicker'
import { courseAccuracy, percent } from './format'

// FR-EX-04: chọn khóa để vào hệ thống bài tập; huy hiệu độ chính xác lấy từ MyCourse.
export function ExercisePickerPage() {
  return (
    <>
    <Link to="/bai-giao" className="mb-4 flex items-center justify-between rounded-lg border border-line-brand bg-subtle p-4 font-extrabold text-brand-strong">
      Bài được giao
      <span aria-hidden>→</span>
    </Link>
    <CoursePicker
      title="Bài tập"
      subtitle="Chọn khóa học để làm bài tập."
      action={(c) => {
        const acc = courseAccuracy(c.answeredQuestions, c.correctQuestions)
        return {
          to: `/bai-tap/khoa/${c.courseId}`,
          label: 'Làm bài tập →',
          badge: acc == null ? undefined : `Độ chính xác · ${percent(acc)}`,
          note: c.answeredQuestions ? `${c.answeredQuestions} câu đã làm` : undefined,
        }
      }}
    />
    </>
  )
}
