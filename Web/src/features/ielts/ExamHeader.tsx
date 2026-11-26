import { Link } from 'react-router'
import { formatClock } from '../practice/useCountdown'
import type { IeltsMode } from './types'

interface Props {
  title: string
  mode: IeltsMode
  secondsLeft: number | null
  canPause: boolean
  paused: boolean
  onPause: () => void
  onFont: () => void
}

const btn = 'grid min-h-11 min-w-11 place-items-center rounded-[12px] border border-line bg-surface px-2 text-sm font-extrabold text-ink'

// Thanh đầu bài (Figma 01.01): quay lại, tên kỹ năng, đồng hồ, cỡ chữ; Tạm dừng chỉ ở Luyện tập (D-67, D-151).
export function ExamHeader({ title, mode, secondsLeft, canPause, paused, onPause, onFont }: Props) {
  return (
    <header className="flex h-20 items-center justify-between gap-2 border-b border-line bg-surface px-3 md:px-7">
      <div className="flex min-w-0 items-center gap-3">
        <Link to="/bai-tap" aria-label="Thoát bài" className={btn}>‹</Link>
        <h1 className="truncate text-base font-black text-ink md:text-lg">{title}</h1>
        <span className="hidden text-xs font-extrabold uppercase text-brand-strong sm:inline">{mode === 'EXAM' ? 'Mô phỏng thi' : 'Luyện tập'}</span>
      </div>
      <div className="flex items-center gap-2">
        {canPause && !paused && <button type="button" onClick={onPause} className={btn}>Tạm dừng</button>}
        {secondsLeft != null && <span role="timer" aria-label="Thời gian còn lại" className="rounded-[12px] bg-subtle px-3 py-2 text-sm font-extrabold text-brand-strong">{formatClock(secondsLeft)}</span>}
        <button type="button" onClick={onFont} aria-label="Đổi cỡ chữ" className={btn}>Aa</button>
      </div>
    </header>
  )
}
