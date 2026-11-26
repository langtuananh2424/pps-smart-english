import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import type { ApiError } from '../../../lib/api'
import { useQuota } from '../../home/api'
import { VN_TZ } from '../../home/format'
import { pillNeutral, pillPrimary } from '../questions/ui'

// Chưa có màn Ví lượt AI riêng (docs/ba/figma-missing-screens A5): tạm mở Đơn hàng. Đổi ở đây khi màn ví có route.
export const WALLET_PATH = '/don-hang'

// Hết lượt: máy chủ trả 402, hoặc /me/quota cho thấy pool và mọi ví khóa đều 0. Lấy lại số liệu (đổi tab về) thì bỏ cờ 402 cũ.
export function useQuotaGate() {
  const quota = useQuota()
  const [hit402, hit] = useState<ApiError | null>(null)
  useEffect(() => hit(null), [quota.dataUpdatedAt])
  const d = quota.data
  const total = d ? d.pool + d.courses.reduce((n, c) => n + c.remaining, 0) : null
  const resets = d?.courses.map((c) => c.resetsAt).sort()[0]
  return { exhausted: hit402 != null || total === 0, hit, resets }
}
export type QuotaGate = ReturnType<typeof useQuotaGate>

const day = (iso: string) => new Date(iso).toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric', timeZone: VN_TZ })

// Thẻ hết lượt (Figma 606-17745): giữ nguyên bài đã làm bên dưới; "Mua thêm lượt" mở tab mới để không mất chỗ đang làm.
export function QuotaNotice({ gate, onSkip }: { gate: QuotaGate; onSkip: () => void }) {
  return (
    <section role="alert" aria-label="Hết lượt AI" className="flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6">
      <h3 className="flex items-center gap-2 text-lg font-black text-ink">
        <span aria-hidden="true" className="grid size-5 place-items-center rounded-full border-[1.5px] border-reward-fg text-xs font-black text-reward-fg">i</span>
        Bạn đã hết lượt chấm AI của khóa này
      </h3>
      <dl className="grid grid-cols-2 gap-3 rounded-md bg-muted px-3 py-2.5 text-sm">
        <div>
          <dt className="font-semibold text-ink-secondary">Lượt còn lại</dt>
          <dd className="font-black text-ink">0 lượt</dd>
        </div>
        {gate.resets && (
          <div>
            <dt className="font-semibold text-ink-secondary">Ngày cộng lượt mới</dt>
            <dd className="font-black text-ink">{day(gate.resets)}</dd>
          </div>
        )}
      </dl>
      <div className="flex flex-wrap gap-3">
        <Link to={WALLET_PATH} target="_blank" rel="noopener" className={pillPrimary}>Mua thêm lượt</Link>
        <button type="button" onClick={onSkip} className={pillNeutral}>Bỏ qua câu này</button>
      </div>
      <p className="text-sm font-semibold text-ink-secondary">Câu bỏ qua sẽ tính 0 điểm.</p>
    </section>
  )
}
