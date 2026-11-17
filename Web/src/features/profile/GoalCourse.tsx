import { Link } from 'react-router'
import { Skeleton } from '../home/BlockState'
import { useMyCourses } from '../home/api'
import { currentCourse } from '../home/format'

// D-73: khóa đang học hoặc học thử; chưa có thì mời xem khóa. Dùng ở Hồ sơ và màn Mục tiêu.
export function GoalCourse() {
  const courses = useMyCourses()
  const c = currentCourse(courses.data)
  const active = c && c.status !== 'EXPIRED' ? c : undefined
  if (courses.isPending) return <Skeleton className="h-5 w-40" />
  if (courses.isError) return <>—</>
  return active ? (
    <>{active.title}</>
  ) : (
    <>Chưa đăng ký · <Link to="/khoa-hoc" className="font-extrabold text-brand-strong underline">Xem khóa học</Link></>
  )
}
