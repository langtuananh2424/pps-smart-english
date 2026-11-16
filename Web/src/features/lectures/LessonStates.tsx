import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router'
import lock from '../../assets/icons/lock.svg'
import { Icon } from '../../components/Icon'
import { ApiError } from '../../lib/api'
import { btnNeutral, btnPrimary } from './ui'

function Notice({ title, body, children }: { title: string; body: string; children: ReactNode }) {
  return (
    <section className="mx-auto mt-6 flex max-w-md flex-col items-center gap-3 rounded-lg border border-line-subtle bg-surface p-8 text-center shadow-edge-card">
      <span className="grid size-14 place-items-center rounded-full bg-subtle text-brand-strong">
        <Icon src={lock} size={24} />
      </span>
      <h1 className="text-2xl font-black leading-[30px] text-ink">{title}</h1>
      <p className="font-semibold text-ink-secondary">{body}</p>
      <div className="mt-2 flex flex-wrap justify-center gap-3">{children}</div>
    </section>
  )
}

// 403 lesson_locked: chưa mua hoặc hết hạn. Server có thể kèm courseSlug trong details để dẫn thẳng đến trang mua.
function LockedNotice({ slug }: { slug?: string }) {
  return (
    <Notice title="Bài giảng chưa mở khóa" body="Bạn chưa có quyền xem bài này. Mua hoặc gia hạn khóa học để tiếp tục.">
      <Link to={slug ? `/khoa-hoc/${slug}` : '/khoa-hoc'} className={btnPrimary}>
        Mua khóa học
      </Link>
      <Link to="/bai-giang" className={btnNeutral}>
        Về danh sách khóa
      </Link>
    </Notice>
  )
}

function LoginNotice() {
  const loc = useLocation()
  const next = encodeURIComponent(loc.pathname)
  return (
    <Notice title="Đăng nhập để xem bài này" body="Bài giảng này dành cho học viên đã đăng nhập.">
      <Link to={`/dang-nhap?next=${next}`} className={btnPrimary}>
        Đăng nhập
      </Link>
    </Notice>
  )
}

export function isLocked(e: unknown) {
  return e instanceof ApiError && e.status === 403 && e.code.toLowerCase() === 'lesson_locked'
}

export function LessonError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  if (isLocked(error)) {
    const d = (error as ApiError).details as { courseSlug?: string } | undefined
    return <LockedNotice slug={d?.courseSlug} />
  }
  if (error instanceof ApiError && error.status === 401) return <LoginNotice />
  const missing = error instanceof ApiError && error.status === 404
  return (
    <Notice title={missing ? 'Không tìm thấy bài giảng' : 'Không tải được bài giảng'} body={missing ? 'Bài giảng không tồn tại hoặc đã bị gỡ.' : 'Kiểm tra kết nối rồi thử lại.'}>
      {!missing && (
        <button type="button" onClick={onRetry} className={btnPrimary}>
          Thử lại
        </button>
      )}
      <Link to="/bai-giang" className={btnNeutral}>
        Về Bài giảng
      </Link>
    </Notice>
  )
}
