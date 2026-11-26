import type { ReactNode } from 'react'
import check from '../../../assets/icons/check.svg'
import lock from '../../../assets/icons/lock.svg'
import { Icon } from '../../../components/Icon'
import { card } from '../../writing/Frame'

// Thẻ trả lời bên phải thẻ đề (Figma 10.26): khóa và dải xanh khi đã gửi chấm.
export function AnswerCard({ title, locked, children }: { title: string; locked: boolean; children: ReactNode }) {
  return (
    <section aria-label={title} className={card}>
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-black text-ink">{title}</h3>
        {locked && <span role="img" aria-label="Đã khóa" className="text-ink-secondary"><Icon src={lock} size={18} /></span>}
      </div>
      {locked && (
        <p role="status" className="flex items-center gap-2 rounded-md bg-success-bg px-3.5 py-2 text-sm font-bold text-success-fg">
          <Icon src={check} size={16} />
          Đã gửi chấm · Kết quả hiện sau khi nộp bài
        </p>
      )}
      {children}
    </section>
  )
}
