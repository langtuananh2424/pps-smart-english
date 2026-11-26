import { Link } from 'react-router'
import { pillNeutral } from '../practice/questions/ui'
import type { Assignment } from './types'
import { CANCELLED_NOTE, titleOf } from './view'

// D-153: bài giao đã hủy, học viên chỉ xem; không có nút bắt đầu hay làm tiếp.
export function CancelledCard({ a }: { a: Assignment }) {
  return (
    <section className="mx-auto mt-6 flex max-w-lg flex-col items-center gap-3 rounded-lg border border-line-subtle bg-surface p-6 text-center shadow-edge-card">
      <h1 className="text-2xl font-black text-ink">{titleOf(a)}</h1>
      <p role="status" className="font-semibold text-ink-secondary">{CANCELLED_NOTE}</p>
      <Link to="/bai-giao" className={pillNeutral}>Về bài được giao</Link>
    </section>
  )
}
