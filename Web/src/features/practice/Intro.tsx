import { Link } from 'react-router'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import { chip, chipTone } from '../lectures/ui'
import { pillNeutral, pillPrimary } from './questions/ui'
import { formatClock } from './useCountdown'
import type { Bank } from './types'

interface Props {
  bank: Bank
  starting: boolean
  error: string | null
  onStart: () => void
}

// Màn giới thiệu: chỉ tạo lượt khi bấm "Bắt đầu" để không tốn lượt thi khi mới mở trang.
export function Intro({ bank, starting, error, onStart }: Props) {
  const exam = bank.mode === 'EXAM'
  return (
    <section className="mx-auto mt-6 flex max-w-lg flex-col items-center gap-4 rounded-lg border border-line-subtle bg-surface p-6 text-center shadow-edge-card sm:p-8">
      <img src={cam} alt="" width={96} height={96} />
      <span className={`${chip} ${exam ? chipTone.brand : chipTone.plain}`}>{exam ? 'Bài thi' : 'Luyện tập'}</span>
      <h1 className="text-2xl font-black leading-[30px] text-ink">{bank.title}</h1>
      <ul className="flex flex-wrap justify-center gap-2 text-sm font-extrabold text-ink-secondary">
        <li className={`${chip} ${chipTone.plain}`}>{bank.questions.length} câu</li>
        {bank.timeLimitSec != null && <li className={`${chip} ${chipTone.plain}`}>Thời gian {formatClock(bank.timeLimitSec)}</li>}
      </ul>
      {exam && <p className="text-sm font-semibold text-ink-secondary">Mỗi lần bấm “Bắt đầu” sẽ tính một lượt thi.</p>}
      {error && <p role="alert" className="text-sm font-bold text-danger">{error}</p>}
      <div className="mt-2 flex flex-wrap justify-center gap-3">
        <button type="button" onClick={onStart} disabled={starting} className={pillPrimary}>
          {starting ? 'Đang bắt đầu…' : 'Bắt đầu'}
        </button>
        <Link to="/bai-tap" className={pillNeutral}>
          Về danh sách
        </Link>
      </div>
    </section>
  )
}
