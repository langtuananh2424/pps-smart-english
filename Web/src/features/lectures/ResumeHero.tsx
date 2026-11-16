import { Link } from 'react-router'
import hero from '../../assets/lectures/hero-writing.webp'
import play from '../../assets/lectures/play-sm.svg'
import type { Resume } from '../home/types'
import { LessonThumb } from './LessonCard'
import { formatClock, percentOf, remainingSec } from './status'

// D-59: "Xem tiếp" mở bài xem gần nhất chưa xong, phát từ vị trí đã dừng.
export function ResumeHero({ resume }: { resume: Resume }) {
  const left = remainingSec(resume.durationSec, resume.positionSec)
  const percent = resume.durationSec ? percentOf(resume.positionSec, resume.durationSec) : 0
  return (
    <section aria-label="Học tiếp" className="relative overflow-hidden rounded-lg border border-line bg-brand-soft">
      <img src={hero} alt="" className="pointer-events-none absolute right-0 top-0 hidden h-full w-[490px] object-cover lg:block" />
      <div className="pointer-events-none absolute right-0 top-0 hidden h-full w-[190px] -translate-x-[300px] bg-gradient-to-r from-[#fff1e3] via-[#fff1e3] via-30% to-transparent lg:block" />
      <div className="relative grid grid-cols-[auto_1fr] gap-x-3 gap-y-3 p-3.5 lg:grid-cols-[300px_452px] lg:gap-x-6 lg:gap-y-2 lg:px-6 lg:py-6">
        <LessonThumb big durationSec={resume.durationSec} positionSec={resume.positionSec} className="h-[68px] w-28 lg:row-span-2 lg:h-[172px] lg:w-[300px]" />
        <div className="flex min-w-0 flex-col items-start gap-2 self-center">
          <span className="rounded-full bg-surface px-2.5 py-[3px] text-xs font-black text-brand-ink">
            {resume.positionSec > 0 ? 'Đang xem dở' : 'Bài tiếp theo'}
            {left != null && left > 0 && <span className="lg:hidden"> · còn {formatClock(left)}</span>}
          </span>
          <h2 className="text-base font-black leading-[22px] text-ink lg:text-2xl lg:leading-[30px]">
            Bài {resume.lessonOrder} · {resume.lessonTitle}
          </h2>
          <p className="text-sm font-bold text-ink-secondary lg:text-base lg:leading-[22px]">{resume.topicTitle}</p>
          {left != null && resume.positionSec > 0 && (
            <div className="hidden max-w-[360px] items-center gap-2.5 self-stretch lg:flex">
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-[#f3d9c3]">
                <div className="h-full bg-brand" style={{ width: `${percent}%` }} />
              </div>
              <span className="text-sm font-black text-ink">Còn {formatClock(left)}</span>
            </div>
          )}
        </div>
        <Link
          to={`/bai-giang/${resume.lessonId}`}
          className="col-span-2 inline-flex min-h-[46px] items-center justify-center gap-2 rounded-full bg-surface text-base font-black text-brand-strong drop-shadow-[0_2px_0_#efe3d8] lg:col-span-1 lg:min-h-[50px] lg:justify-self-start lg:px-6"
        >
          <img src={play} alt="" className="size-4" />
          Xem tiếp
        </Link>
      </div>
    </section>
  )
}
