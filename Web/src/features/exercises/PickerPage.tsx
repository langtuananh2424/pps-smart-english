import { CoursePicker } from '../lectures/CoursePicker'
import { courseAccuracy, percent } from './format'

// FR-EX-04: chọn khóa để vào hệ thống bài tập; huy hiệu độ chính xác lấy từ MyCourse.
export function ExercisePickerPage() {
  return (
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
  )
}
