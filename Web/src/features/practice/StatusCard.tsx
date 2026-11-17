import type { ReactNode } from 'react'
import { Link } from 'react-router'
import lock from '../../assets/icons/lock.svg'
import { Icon } from '../../components/Icon'
import { ApiError } from '../../lib/api'
import { pillNeutral, pillPrimary } from './questions/ui'

export function Card({ title, body, children }: { title: string; body: string; children?: ReactNode }) {
  return (
    <section role="alert" className="mx-auto mt-10 flex max-w-md flex-col items-center gap-3 rounded-lg border border-line-subtle bg-surface p-8 text-center shadow-edge-card">
      <span className="grid size-14 place-items-center rounded-full bg-subtle text-brand-strong">
        <Icon src={lock} size={24} />
      </span>
      <h1 className="text-2xl font-black leading-[30px] text-ink">{title}</h1>
      <p className="font-semibold text-ink-secondary">{body}</p>
      <div className="mt-2 flex flex-wrap justify-center gap-3">{children}</div>
    </section>
  )
}

interface Info { title: string; body: string }
const num = (d: unknown, k: string) => (d as Record<string, number> | undefined)?.[k]

// Lỗi mở bộ / bắt đầu lượt (SRS FR-EX-06, D-68) thành nội dung thẻ trạng thái.
export function describeError(e: unknown): Info {
  if (!(e instanceof ApiError)) return { title: 'Không tải được bài tập', body: 'Kiểm tra kết nối rồi thử lại.' }
  switch (e.code) {
    case 'bank_not_found':
      return { title: 'Không tìm thấy bộ bài tập', body: 'Bộ này không tồn tại hoặc đã bị gỡ.' }
    case 'bank_locked':
    case 'exam_requires_enrollment':
      return { title: 'Bộ bài tập đang khóa', body: 'Bạn cần đăng ký khóa học chứa bộ này để làm bài.' }
    case 'exam_locked':
      return { title: 'Bài thi chưa mở', body: `Cần hoàn thành ≥ ${num(e.details, 'requiredPercent')}% bài học để mở, hiện ${num(e.details, 'progressPercent')}%.` }
    case 'exam_not_started':
      return { title: 'Bài thi chưa mở', body: 'Bài thi này chưa đến giờ mở.' }
    case 'exam_attempts_exhausted':
      return { title: 'Hết lượt thi', body: `Bạn đã dùng ${num(e.details, 'used')}/${num(e.details, 'allowed')} lượt thi của bài này.` }
    default:
      return { title: 'Không tải được bài tập', body: e.message }
  }
}

export function ErrorCard({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { title, body } = describeError(error)
  const retryable = !(error instanceof ApiError) || error.status >= 500
  return (
    <Card title={title} body={body}>
      {retryable && onRetry && (
        <button type="button" onClick={onRetry} className={pillPrimary}>
          Thử lại
        </button>
      )}
      <Link to="/bai-tap" className={pillNeutral}>
        Về danh sách bài tập
      </Link>
      <Link to="/khoa-hoc" className={pillNeutral}>
        Xem khóa học
      </Link>
    </Card>
  )
}
