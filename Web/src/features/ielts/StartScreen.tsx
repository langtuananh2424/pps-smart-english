import { Link } from 'react-router'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import type { Bank } from '../practice/types'
import { formatClock } from '../practice/useCountdown'
import type { IeltsMode } from './types'

interface Props {
  bank: Bank
  mode: IeltsMode
  onMode: (m: IeltsMode) => void
  starting: boolean
  error: string | null
  onStart: () => void
}

const MODES: { value: IeltsMode; label: string; note: string }[] = [
  { value: 'PRACTICE', label: 'Luyện tập', note: 'Được tạm dừng, có Cam gợi ý theo câu.' },
  { value: 'EXAM', label: 'Mô phỏng thi', note: 'Đồng hồ chạy liên tục, không tạm dừng, không gợi ý.' },
]

// Figma 00 · Bắt đầu: chọn chế độ làm bài rồi mới tạo lượt (không tốn lượt khi mới mở trang).
export function StartScreen({ bank, mode, onMode, starting, error, onStart }: Props) {
  const skill = bank.skill === 'LISTENING' ? 'Listening' : 'Reading'
  return (
    <div className="min-h-dvh bg-page">
      <header className="flex h-[76px] items-center justify-between border-b border-line bg-surface px-4 md:px-9">
        <span className="text-lg font-black text-ink">IELTS {skill}</span>
        <div role="radiogroup" aria-label="Chế độ làm bài" className="flex gap-1.5">
          {MODES.map((m) => (
            <button
              key={m.value}
              type="button"
              role="radio"
              aria-checked={mode === m.value}
              disabled={starting}
              onClick={() => onMode(m.value)}
              className={`min-h-11 rounded-full border-[1.5px] px-3.5 text-sm font-extrabold ${mode === m.value ? 'border-brand bg-subtle text-brand-strong' : 'border-line bg-surface text-ink-secondary'}`}
            >
              {m.label}
            </button>
          ))}
        </div>
      </header>
      <section className="mx-auto mt-8 flex max-w-lg flex-col items-center gap-4 rounded-lg border border-line-subtle bg-surface p-6 text-center shadow-edge-card sm:p-8">
        <img src={cam} alt="" width={96} height={96} />
        <p className="text-xs font-extrabold uppercase tracking-wide text-brand-strong">IELTS Academic · {MODES.find((m) => m.value === mode)?.label}</p>
        <h1 className="text-2xl font-black leading-[30px] text-ink">{bank.title}</h1>
        <p className="text-sm font-semibold text-ink-secondary">
          {bank.questions.length} câu{bank.timeLimitSec != null && ` · Thời gian ${formatClock(bank.timeLimitSec)}`}
        </p>
        <p className="text-sm font-semibold text-ink-secondary">{MODES.find((m) => m.value === mode)?.note}</p>
        {error && <p role="alert" className="text-sm font-bold text-danger">{error}</p>}
        <div className="mt-2 flex flex-wrap justify-center gap-3">
          <button type="button" onClick={onStart} disabled={starting} className={pillPrimary}>
            {starting ? 'Đang bắt đầu…' : 'Bắt đầu'}
          </button>
          <Link to="/bai-tap" className={pillNeutral}>Về danh sách</Link>
        </div>
      </section>
    </div>
  )
}
