import { Link, useParams } from 'react-router'
import { useMyCourses } from '../home/api'
import { CourseTests } from './CourseTests'

// Màn riêng của danh sách kiểm tra trong khóa; cùng nội dung tab "Bài kiểm tra" ở chi tiết khóa.
export function CourseTestsPage() {
  const courseId = Number(useParams().courseId)
  const course = useMyCourses().data?.find((c) => c.courseId === courseId)
  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <header className="flex flex-col gap-1">
        <Link to={`/bai-tap/khoa/${courseId}`} className="text-sm font-extrabold text-brand-strong">
          ← Hệ thống bài tập
        </Link>
        <h1 className="text-2xl font-black leading-[30px] text-ink lg:text-[30px] lg:leading-[38px]">Bài kiểm tra</h1>
        {course && <p className="font-semibold text-ink-secondary">{course.title}</p>}
      </header>
      <CourseTests courseId={courseId} slug={course?.slug} />
    </div>
  )
}
