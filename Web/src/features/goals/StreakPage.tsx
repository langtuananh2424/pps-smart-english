import { Link } from 'react-router'
import cam from '../../assets/profile/cam-huy-hieu.png'
import { BlockError, Skeleton } from '../home/BlockState'
import { useMeStats, useMyCourses, useRoadmap } from '../home/api'
import { currentCourse, formatXp } from '../home/format'

const btn = 'flex h-12 items-center justify-center rounded-sm px-4 text-[15px] font-extrabold max-sm:w-full'

// Đích giống nút "Học tiếp ngay" ở Trang chủ (ResumeCard).
function ContinueLink() {
  const courses = useMyCourses()
  const course = currentCourse(courses.data)
  const resume = useRoadmap(course?.courseId).data?.resume
  const to = resume ? `/bai-giang/${resume.lessonId}` : course ? `/khoa-hoc/${course.slug}` : '/khoa-hoc'
  return <Link to={to} className={`${btn} bg-brand-strong text-white`}>Học tiếp ngay</Link>
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-1">
      <dt className="text-[13px] font-extrabold leading-[18px] text-ink-secondary">{label}</dt>
      <dd className="text-base font-extrabold leading-[22px] text-ink">{value}</dd>
    </div>
  )
}

// FR-AUTH-03 (streak/XP): chi tiết chuỗi học từ GET /api/me/stats.
export function StreakPage() {
  const stats = useMeStats()
  const s = stats.data
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-[27px] font-black leading-[37px] text-ink lg:text-4xl lg:leading-[50px]">Chuỗi học của bạn</h1>
      {/* Backend giữ chuỗi khi hoàn thành bài giảng hoặc nộp bài tập (RetentionListener). */}
      <p className="text-[15px] font-semibold text-ink-secondary">Hoàn thành một bài giảng hoặc nộp một bài tập hôm nay để giữ chuỗi.</p>
      {stats.isError ? (
        <BlockError onRetry={() => stats.refetch()} />
      ) : !s ? (
        <Skeleton className="h-[200px] w-full rounded-lg" />
      ) : (
        <section aria-label="Chuỗi hiện tại" className="flex flex-col gap-3 rounded-[24px] border border-line-subtle bg-reward-bg p-5 lg:rounded-[28px]">
          <div className="flex items-center gap-3">
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <p className="text-[25px] font-black leading-[35px] text-brand-strong lg:text-4xl lg:leading-[50px]">
                {s.streakCurrent > 0 ? `${s.streakCurrent} ngày liên tiếp` : 'Chưa có chuỗi'}
              </p>
              <p className="text-[15px] font-semibold text-ink-secondary">
                {s.activeToday
                  ? 'Hôm nay đã học'
                  : s.streakCurrent > 0
                    ? 'Hôm nay chưa ghi nhận hoạt động học.'
                    : 'Học một bài hôm nay để bắt đầu chuỗi mới.'}
              </p>
            </div>
            <img src={cam} alt="" className="size-[72px] shrink-0 object-contain lg:size-[104px]" />
          </div>
          <dl className="flex gap-3">
            <Stat label="Chuỗi dài nhất" value={`${s.streakBest} ngày`} />
            <Stat label="Tổng XP" value={formatXp(s.xp)} />
          </dl>
        </section>
      )}
      <div className="flex flex-col gap-2 sm:flex-row">
        <ContinueLink />
        <Link to="/ho-so" className={`${btn} border border-line bg-surface text-brand-strong`}>Về hồ sơ</Link>
      </div>
    </div>
  )
}
