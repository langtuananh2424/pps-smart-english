import { Link } from 'react-router'
import cam from '../../assets/home/cam-he-dau.png'
import illustration from '../../assets/home/minh-hoa.webp'
import play from '../../assets/icons/play.svg'
import { Icon } from '../../components/Icon'
import { currentCourse } from './format'
import { useMyCourses, useRoadmap } from './api'
import { BlockError, Skeleton } from './BlockState'

const shell = 'relative overflow-hidden rounded-lg border border-line bg-brand-soft shadow-edge-primary'
const pill = 'inline-block self-start rounded-full bg-surface/90 px-3 py-[5px] text-xs font-extrabold leading-4 text-brand-ink'
const secondaryBtn = 'inline-flex min-h-[52px] items-center justify-center gap-[10px] rounded-full bg-surface px-7 text-lg font-black text-brand-strong shadow-edge-card'

// Thẻ lớn đầu trang: học tiếp, đã xong khóa, hoặc mời bắt đầu.
export function ResumeCard() {
  const courses = useMyCourses()
  const course = currentCourse(courses.data)
  const roadmap = useRoadmap(course?.courseId)
  const resume = roadmap.data?.resume

  let body
  if (courses.isError) body = <BlockError onRetry={() => courses.refetch()} />
  else if (courses.isPending || (course && roadmap.isPending)) body = <Skeleton className="h-40 w-full max-w-md" />
  else if (!course)
    body = (
      <>
        <h2 className="text-2xl font-black leading-[30px] text-ink lg:text-[30px] lg:leading-[38px]">Bắt đầu hành trình</h2>
        <p className="font-semibold text-ink-secondary">Chọn khóa học hoặc làm bài kiểm tra đầu vào để biết trình độ của bạn.</p>
        <div className="flex flex-wrap gap-3">
          <Link to="/khoa-hoc" className={secondaryBtn}>Xem khóa học</Link>
          <Link to="/kiem-tra-dau-vao" className="inline-flex min-h-[52px] items-center rounded-full border-2 border-brand px-6 text-base font-extrabold text-brand-strong">
            Kiểm tra đầu vào
          </Link>
        </div>
      </>
    )
  else if (roadmap.isError) body = <BlockError onRetry={() => roadmap.refetch()} />
  else if (!resume)
    body = (
      <>
        <h2 className="text-2xl font-black leading-[30px] text-ink lg:text-[30px] lg:leading-[38px]">Hôm nay bạn học gì?</h2>
        <p className="text-lg font-extrabold text-ink">Bạn đã học xong khóa này.</p>
        <Link to={`/khoa-hoc/${course.slug}`} className={secondaryBtn}>Xem lộ trình</Link>
      </>
    )
  else
    body = (
      <>
        <span className={pill}>
          {resume.topicTitle} · {resume.levelTitle}
        </span>
        <h2 className="text-2xl font-black leading-[30px] text-ink lg:text-[30px] lg:leading-[38px]">Hôm nay bạn học gì?</h2>
        <p className="text-lg font-extrabold leading-6 text-ink">
          Bài {resume.lessonOrder} · {resume.lessonTitle}
        </p>
        <div className="flex w-full max-w-[420px] items-center gap-3">
          <div
            role="progressbar"
            aria-label="Tiến độ chủ đề"
            aria-valuemin={0}
            aria-valuemax={resume.totalInTopic}
            aria-valuenow={resume.completedInTopic}
            className="h-2 flex-1 overflow-hidden rounded-full bg-line-input lg:h-[10px]"
          >
            <div className="h-full rounded-full bg-brand" style={{ width: `${resume.totalInTopic ? (resume.completedInTopic / resume.totalInTopic) * 100 : 0}%` }} />
          </div>
          <span className="text-lg font-extrabold text-ink">
            {resume.completedInTopic}/{resume.totalInTopic}
            <span className="max-sm:sr-only"> bài</span>
          </span>
        </div>
        <Link to={`/bai-giang/${resume.lessonId}`} className={`${secondaryBtn} mt-1`}>
          <span className="grid size-7 place-items-center rounded-full bg-brand text-white">
            <Icon src={play} size={12} />
          </span>
          Học tiếp ngay
        </Link>
      </>
    )

  return (
    <section aria-label="Tiếp tục học" className={shell}>
      <img src={illustration} alt="" className="pointer-events-none absolute right-0 top-0 h-[118px] w-[154px] object-cover md:h-full md:w-[351px]" />
      <div className="pointer-events-none absolute right-0 top-0 h-[118px] w-[154px] bg-gradient-to-r from-[#fff1e3] via-[#fff1e3]/40 to-transparent md:h-full md:w-[351px]" />
      <img src={cam} alt="" className="pointer-events-none absolute right-5 top-5 size-24 object-contain md:right-28 md:top-14 md:size-[175px]" />
      <div className="relative flex flex-col items-start gap-3 p-5 md:max-w-[60%] md:gap-[14px] md:px-7 md:py-7">{body}</div>
    </section>
  )
}
