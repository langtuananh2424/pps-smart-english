import { Link } from 'react-router'
import logo from '../../assets/logo-pps.png'
import { pillNeutral } from './questions/ui'
import type { DraftStatus } from './useDraft'
import { formatClock } from './useCountdown'

const draftText: Record<DraftStatus, string> = { idle: '', saving: 'Đang lưu…', saved: 'Đã lưu', failed: 'Lưu thất bại' }

interface Props {
  title: string
  secondsLeft: number | null
  draft: DraftStatus
  canPause: boolean
  paused: boolean
  onPause: () => void
}

// Thanh đầu bài (Figma 10.39): quay lại, logo, tên bộ; bên phải đồng hồ và nút tạm dừng.
export function PlayHeader({ title, secondsLeft, draft, canPause, paused, onPause }: Props) {
  return (
    <header className="sticky top-0 z-20 flex h-[68px] items-center justify-between gap-3 border-b border-line bg-surface px-4 md:h-[72px] md:px-7">
      <div className="flex min-w-0 items-center gap-2 md:gap-3.5">
        <Link to="/bai-tap" aria-label="Về danh sách bài tập" className="grid size-11 shrink-0 place-items-center rounded-[10px] border border-line bg-input text-[13px] font-extrabold text-brand-strong">
          ‹
        </Link>
        <img src={logo} alt="PPS Smart English" className="h-[22px] w-auto md:h-[29px]" />
        <h1 className="hidden truncate text-lg font-black leading-6 text-ink md:block">{title}</h1>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <span role="status" className={`text-xs font-extrabold ${draft === 'failed' ? 'text-danger' : 'text-ink-muted'}`}>
          {draftText[draft]}
        </span>
        {secondsLeft != null && (
          <span aria-label="Thời gian còn lại" className="rounded-full bg-subtle px-3.5 py-2 text-sm font-black tabular-nums text-brand-ink">
            {formatClock(secondsLeft)}
          </span>
        )}
        {canPause && !paused && (
          <button type="button" onClick={onPause} className={`${pillNeutral} !h-11 !text-sm`}>
            Tạm dừng
          </button>
        )}
      </div>
    </header>
  )
}
