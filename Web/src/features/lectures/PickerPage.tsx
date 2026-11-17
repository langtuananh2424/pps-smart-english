import { CoursePicker } from './CoursePicker'
import { courseChip } from './status'

export function PickerPage() {
  return (
    <CoursePicker
      title="Bài giảng"
      subtitle="Chọn khóa học để xem bài giảng."
      action={(c) => ({
        to: `/bai-giang/khoa/${c.courseId}`,
        label: courseChip(c).tone === 'success' ? 'Xem lại bài giảng' : 'Xem bài giảng →',
      })}
    />
  )
}
