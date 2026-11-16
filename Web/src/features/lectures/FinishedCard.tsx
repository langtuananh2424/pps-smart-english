import { Link } from 'react-router'
import { btnNeutral, btnPrimary } from './ui'

// Hiện sau khi xem xong; nút bài tập chỉ có khi bài có bộ bài tập (banks.lessonId).
export function FinishedCard({ order, bankId, listHref }: { order: number | null; bankId: number | null; listHref: string }) {
  const n = order != null ? ` Bài ${order}` : ''
  return (
    <section aria-label="Xem xong bài" className="flex flex-col gap-4 rounded-lg border-2 border-brand bg-page p-[18px] lg:flex-row lg:items-center lg:gap-6 lg:px-6 lg:py-5">
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <h2 className="text-lg font-black leading-6 text-ink">Bạn đã xem xong{n || ' bài này'}</h2>
        <p className="text-sm font-semibold leading-5 text-ink-secondary">
          {bankId != null ? 'Làm bài tập ngay để ghi nhớ kiến thức vừa học.' : 'Chọn bài tiếp theo trong danh sách để học tiếp.'}
        </p>
      </div>
      <div className="flex flex-col gap-3 lg:flex-row-reverse lg:items-center">
        {bankId != null && (
          <Link to={`/bai-tap/${bankId}`} className={`${btnPrimary} rounded-md lg:px-5`}>
            Làm bài tập{n} →
          </Link>
        )}
        <Link to={listHref} className={`${btnNeutral} rounded-md`}>
          Danh sách bài
        </Link>
      </div>
    </section>
  )
}
