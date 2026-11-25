import { Link, useParams } from 'react-router'
import { ApiError } from '../../lib/api'
import { formatVnd } from '../checkout/format'
import { Field, StatusBadge } from '../checkout/ui'
import { fmtTime } from './OrdersPage'
import { useStudent } from './studentsApi'
import { Loading, PageHead, RetryError, Section } from './ui'

const ACCOUNT: Record<string, string> = { ACTIVE: 'Đang hoạt động', LOCKED: 'Bị khóa' }
const ENROLL: Record<string, string> = { ACTIVE: 'Còn hiệu lực', EXPIRED: 'Hết hạn', REVOKED: 'Đã thu hồi' }
const row = 'grid gap-1 border-b border-line-brand p-2.5 last:border-b-0 md:grid-cols-3 md:items-center md:gap-4'

// FR-SUP-03: hồ sơ học viên, chỉ xem. Các thao tác hỗ trợ (kích hoạt, cộng lượt bù, mở khóa, gửi lại email) là màn riêng.
export function StudentProfilePage() {
  const { id = '' } = useParams()
  const q = useStudent(id)
  const d = q.data

  return (
    <>
      <Link to="/quan-tri/hoc-vien" className="inline-flex min-h-11 items-center self-start font-extrabold text-brand-strong underline">
        Tra cứu học viên
      </Link>
      <PageHead kicker="Nhân viên hỗ trợ" title="Hồ sơ học viên" sub="Tra cứu quyền học, lượt AI, đơn hàng và ticket." />
      {q.isPending ? (
        <Loading text="Đang tải hồ sơ…" />
      ) : q.error instanceof ApiError && q.error.status === 404 ? (
        <p role="alert" className="font-bold text-ink-secondary">
          Không tìm thấy học viên.
        </p>
      ) : q.isError || !d ? (
        <RetryError error={q.error} onRetry={() => q.refetch()} title="Không thể tải hồ sơ" />
      ) : (
        <>
          <Section title="Thông tin tài khoản">
            <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <Field label="Họ tên">{d.student.displayName ?? '—'}</Field>
              <Field label="Email">{d.student.email}</Field>
              <Field label="Trạng thái">{ACCOUNT[d.student.status] ?? d.student.status}</Field>
              <Field label="Xác thực email">{d.student.emailVerified ? 'Đã xác thực' : 'Chưa xác thực'}</Field>
              <Field label="Ngày tạo">{fmtTime(d.createdAt)}</Field>
            </dl>
          </Section>
          <Section title="Khóa học và hạn">
            {d.enrollments.length === 0 ? (
              <p className="font-semibold text-ink-secondary">Chưa có quyền học nào.</p>
            ) : (
              <ul>
                {d.enrollments.map((e) => (
                  <li key={e.courseId} className={row}>
                    <span className="font-extrabold text-ink">{e.courseTitle}</span>
                    <span className="font-semibold text-ink-secondary">{ENROLL[e.status] ?? e.status}</span>
                    <span className="font-semibold text-ink-secondary">{e.expiresAt ? `Hạn ${fmtTime(e.expiresAt)}` : 'Không thời hạn'}</span>
                  </li>
                ))}
              </ul>
            )}
          </Section>
          <Section title="Đơn gần nhất">
            {d.orders.length === 0 ? (
              <p className="font-semibold text-ink-secondary">Chưa có đơn hàng nào.</p>
            ) : (
              <ul>
                {d.orders.map((o) => (
                  <li key={o.code} className={row}>
                    <span className="font-extrabold text-ink">{o.code}</span>
                    <span>
                      <StatusBadge status={o.status} />
                    </span>
                    <span className="font-semibold text-ink-secondary">
                      {formatVnd(o.totalVnd)} · {fmtTime(o.paidAt ?? o.createdAt)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Section>
          <Section title="Ví lượt AI">
            <p className="font-extrabold text-ink">Lượt mua thêm còn lại: {d.quota.pool}</p>
            {d.quota.courses.length === 0 ? (
              <p className="font-semibold text-ink-secondary">Chưa có hạn mức theo khóa.</p>
            ) : (
              <ul>
                {d.quota.courses.map((c) => (
                  <li key={c.courseId} className={row}>
                    <span className="font-extrabold text-ink">{c.courseTitle}</span>
                    <span className="font-semibold text-ink-secondary">
                      Còn {c.remaining}/{c.quota} lượt
                    </span>
                    <span className="font-semibold text-ink-secondary">Đã dùng {c.used}</span>
                  </li>
                ))}
              </ul>
            )}
          </Section>
        </>
      )}
    </>
  )
}
