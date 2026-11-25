import type { ReactNode } from 'react'
import { pillNeutral } from '../questions/ui'
import { card, Frame } from '../../writing/Frame'

// Bài Viết/Nói trong lượt đã chấm xong: chưa lộ điểm hay bài sửa, chỉ hiện sau khi nộp cả bộ.
export function Sealed({ title, left, onRedo, busy, error }: { title: string; left?: ReactNode; onRedo: () => void; busy?: boolean; error?: string | null }) {
  const redo = <button type="button" disabled={busy} onClick={onRedo} className={pillNeutral}>{busy ? 'Đang tạo…' : title === 'Luyện nói' ? 'Nói lại' : 'Viết lại'}</button>
  return (
    <Frame title={title} left={left} actions={redo}>
      <section aria-label="Đã chấm" className={card}>
        <p role="status" className="text-base font-extrabold text-brand-ink">Đã chấm xong. Điểm và nhận xét sẽ hiện sau khi bạn nộp bài.</p>
        <p className="text-sm font-semibold text-ink-secondary">Làm lại thì bài mới được tính thay bài này.</p>
        {error && <p role="alert" className="rounded-md bg-danger-bg p-3 text-sm font-bold text-danger-fg">{error}</p>}
      </section>
    </Frame>
  )
}
