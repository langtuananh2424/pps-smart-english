import { Link } from 'react-router'
import { btnNeutral } from '../lectures/ui'

// Khóa chưa có bộ bài tập nào: báo rõ thay vì để trang trống, dẫn sang bài giảng.
export function EmptyCard({ courseId }: { courseId: number }) {
  return (
    <section aria-label="Chưa có bài tập" className="flex flex-col items-center gap-2 rounded-lg border-2 border-dashed border-line px-4 py-6 text-center">
      <h2 className="text-lg font-black leading-6 text-ink">Khóa học này chưa có bài tập.</h2>
      <p className="text-sm font-semibold leading-5 text-ink-secondary">Bài tập sẽ hiện ở đây khi được đăng.</p>
      <Link to={`/bai-giang/khoa/${courseId}`} className={`${btnNeutral} mt-1`}>
        Xem bài giảng →
      </Link>
    </section>
  )
}
