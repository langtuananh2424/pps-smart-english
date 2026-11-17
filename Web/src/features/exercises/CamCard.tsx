import cam from '../../assets/exercises/cam-kinh-lup.webp'
import { labelOf, percent } from './format'
import type { ExerciseStats } from './types'

// D-62: gợi ý theo quy tắc (không gọi AI); chỉ hiện khi có dạng đủ 10 câu. Không có nút luyện 10 câu (D-64).
export function CamCard({ weakest }: { weakest: ExerciseStats['weakest'] }) {
  if (!weakest) return null
  return (
    <section aria-label="Cam gợi ý" className="flex items-center gap-3.5 rounded-lg border border-line-subtle bg-brand-soft p-3.5 shadow-edge-card lg:gap-5 lg:rounded-[28px] lg:px-6 lg:py-5">
      <img src={cam} alt="" className="size-16 shrink-0 self-start lg:size-[108px] lg:self-center" />
      <div className="flex min-w-0 flex-col items-start gap-[3px] lg:gap-1.5">
        <span className="rounded-full bg-subtle px-2.5 py-[3px] text-[11px] font-black text-brand-strong lg:text-xs">
          <span className="lg:hidden">Cam gợi ý</span>
          <span className="max-lg:hidden">Cam gợi ý luyện tập</span>
        </span>
        <h2 className="text-base font-black leading-[21px] text-ink lg:text-2xl lg:leading-[30px]">{labelOf(weakest.type)}</h2>
        <p className="text-[13px] font-semibold text-ink-secondary lg:text-[15px]">
          Bạn đúng <b className="font-black text-danger-edge">{percent(weakest.accuracy)}</b>, thấp nhất trong khóa.
        </p>
      </div>
    </section>
  )
}
