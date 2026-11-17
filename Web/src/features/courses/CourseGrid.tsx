import { useRoadmap } from '../home/api'
import type { MyCourse } from '../home/types'
import { useProgress } from './api'
import { CourseCardView, type CardView } from './CourseCard'
import type { CourseSummary } from './types'
import type { Trial } from './useTrial'

interface Ctx {
  enrolled: Map<string, MyCourse>
  suggested: Set<string>
  trial: Trial
}

// Khóa chưa ghi danh: gợi ý > học thử > mặc định.
function plainView(c: CourseSummary, { suggested, trial }: Ctx): CardView {
  const view = { label: 'Xem khóa học', to: `/khoa-hoc/${c.slug}` }
  if (suggested.has(c.slug)) return { badge: { text: 'Phù hợp với bạn', tone: 'brand' }, action: view, highlight: true }
  if (c.trialLessonCount > 0) {
    return {
      badge: { text: `Học thử miễn phí ${c.trialLessonCount} bài`, tone: 'reward' },
      action: { label: 'Học thử', onClick: () => trial.open(c.slug), disabled: trial.busy === c.slug },
    }
  }
  return { action: view }
}

// Chỉ khóa đã ghi danh mới gọi progress/roadmap.
function EnrolledCard({ course, mine }: { course: CourseSummary; mine: MyCourse }) {
  const progress = useProgress(mine.courseId)
  const roadmap = useRoadmap(mine.status !== 'EXPIRED' ? mine.courseId : undefined)
  const percent = progress.data?.percent
  const detail = `/khoa-hoc/${course.slug}`
  let view: CardView
  const resume = roadmap.data?.resume
  // D-61: khóa học thử không phải khóa đang học; mua mới có "Học tiếp" đầy đủ.
  if (mine.status === 'TRIAL') view = { badge: { text: 'Học thử', tone: 'reward' }, action: { label: 'Học thử', to: resume ? `/bai-giang/${resume.lessonId}` : detail } }
  else if (percent === 100) view = { badge: { text: 'Đã hoàn thành', tone: 'success' }, action: { label: 'Ôn lại', to: detail } }
  else if (mine.status === 'ACTIVE') {
    view = {
      badge: { text: percent == null ? 'Đang học' : `Đang học · ${percent}%`, tone: 'brand' },
      percent,
      active: true,
      action: { label: 'Học tiếp', to: resume ? `/bai-giang/${resume.lessonId}` : detail },
    }
  } else view = { badge: { text: 'Hết hạn', tone: 'plain' }, action: { label: 'Gia hạn', to: `/thanh-toan?khoa=${course.slug}` } }
  return <CourseCardView course={course} view={view} />
}

function Item({ course, ctx }: { course: CourseSummary; ctx: Ctx }) {
  const mine = ctx.enrolled.get(course.slug)
  return mine ? <EnrolledCard course={course} mine={mine} /> : <CourseCardView course={course} view={plainView(course, ctx)} />
}

// Desktop: hàng đầu 2 thẻ lớn, còn lại lưới 4 cột; mobile 1 cột.
export function CourseGrid({ courses, ...ctx }: Ctx & { courses: CourseSummary[] }) {
  return (
    <div className="flex flex-col gap-3 lg:gap-5">
      <div className="grid gap-3 lg:grid-cols-[1.35fr_1fr] lg:gap-5">
        {courses.slice(0, 2).map((c) => (
          <Item key={c.slug} course={c} ctx={ctx} />
        ))}
      </div>
      {courses.length > 2 && (
        <div className="grid gap-3 md:grid-cols-2 lg:gap-5 xl:grid-cols-4">
          {courses.slice(2).map((c) => (
            <Item key={c.slug} course={c} ctx={ctx} />
          ))}
        </div>
      )}
    </div>
  )
}
