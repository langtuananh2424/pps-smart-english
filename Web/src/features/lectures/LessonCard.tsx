import { Link } from 'react-router'
import art from '../../assets/lectures/thumb-art.svg'
import artLg from '../../assets/lectures/thumb-art-lg.svg'
import play from '../../assets/lectures/thumb-play.svg'
import playLg from '../../assets/lectures/thumb-play-lg.svg'
import type { RoadmapBank, RoadmapLesson } from '../home/types'
import { chip, btnNeutral } from './ui'
import { formatClock, lessonStatus, percentOf, remainingSec, statusLabel, type LessonStatus } from './status'

const frame: Record<LessonStatus, string> = { DONE: 'bg-success-bg', WATCHING: 'bg-brand-soft', NEW: 'bg-surface' }
const badge: Record<LessonStatus, string> = {
  DONE: 'bg-success-bg text-success-fg',
  WATCHING: 'bg-subtle text-brand-strong',
  NEW: 'bg-muted text-ink-secondary',
}

// Thumbnail giữ chỗ (chưa có ảnh bài giảng trong dữ liệu): nền vàng, nút phát, thời lượng, thanh tiến độ.
export function LessonThumb({ durationSec, positionSec, big, className = '' }: { durationSec: number | null; positionSec?: number | null; big?: boolean; className?: string }) {
  const percent = durationSec && positionSec ? percentOf(positionSec, durationSec) : 0
  return (
    <div className={`relative flex shrink-0 items-center justify-center overflow-hidden bg-gradient-to-br from-[#fef3c7] to-[#fcd34d] ${big ? 'rounded-md border-[3px] border-white/85' : 'rounded-sm'} ${className}`}>
      <img src={big ? artLg : art} alt="" className={`absolute left-[56%] top-[33%] opacity-35 ${big ? 'w-[50%]' : 'w-[51%]'}`} />
      <span className={`relative grid place-items-center rounded-full bg-surface ${big ? 'size-9 lg:size-[70px]' : 'size-10 shadow-edge-card max-lg:size-[22px]'}`}>
        <img src={big ? playLg : play} alt="" className={big ? 'size-4 lg:size-[33px]' : 'size-[19px] max-lg:size-2.5'} />
      </span>
      {durationSec != null && (
        <span className="absolute left-[69%] top-[72%] rounded-md bg-ink/80 px-[7px] py-0.5 text-[11px] font-extrabold leading-[15px] text-white max-lg:rounded-[3px] max-lg:px-1 max-lg:text-[8px] max-lg:leading-[10px]">
          {formatClock(durationSec)}
        </span>
      )}
      {percent > 0 && (
        <span className="absolute inset-x-0 bottom-0 h-[5px] bg-surface/60 max-lg:h-[3.6px]">
          <span className="block h-full bg-brand" style={{ width: `${percent}%` }} />
        </span>
      )}
    </div>
  )
}

export function LessonCard({ lesson, bank }: { lesson: RoadmapLesson; bank?: RoadmapBank }) {
  const status = lessonStatus(lesson)
  const left = status === 'WATCHING' ? remainingSec(lesson.durationSec, lesson.maxPositionSec) : null
  const label = left != null && left > 0 ? `Đang xem · còn ${formatClock(left)}` : statusLabel[status]
  const href = `/bai-giang/${lesson.id}`
  return (
    <div className={`relative flex items-center gap-3 rounded-lg border border-line p-2.5 shadow-edge-card lg:gap-3.5 ${frame[status]}`}>
      <LessonThumb durationSec={lesson.durationSec} positionSec={lesson.maxPositionSec} className="h-[52px] w-[86px] max-lg:self-center lg:h-24 lg:w-[168px]" />
      <div className="flex min-w-0 flex-1 flex-col items-start gap-1 lg:gap-1.5">
        <span className={`${chip} ${badge[status]}`}>{label}</span>
        <h3 className="text-base font-black leading-[21px] text-ink">
          {/* Liên kết phủ cả thẻ; nút bài tập nằm trên lớp phủ. */}
          <Link to={href} className="after:absolute after:inset-0 after:rounded-lg">
            Bài {lesson.order} · {lesson.title}
          </Link>
        </h3>
        {bank && (
          <Link to={`/bai-tap/${bank.id}`} className={`${btnNeutral} relative z-[1] min-h-[38px] py-[7px] max-lg:px-3.5`}>
            Làm bài tập →
          </Link>
        )}
      </div>
    </div>
  )
}
