import { Link } from 'react-router'
import cam from '../../assets/profile/cam-huy-hieu.png'
import { Skeleton } from '../home/BlockState'
import { useMeStats } from '../home/api'
import { formatDate, useGoalTarget } from '../goals/api'
import { levelLabel, type Me } from './api'
import { GoalCourse } from './GoalCourse'

const outline = 'flex h-12 items-center justify-center rounded-sm border border-line bg-surface px-4 text-[15px] font-extrabold text-brand-strong'
const card = 'flex min-w-0 flex-1 flex-col gap-2.5 rounded-[22px] border border-line bg-surface p-4 lg:rounded-lg lg:p-6'
const DASH = '—'

// Chữ viết tắt cho avatar: chữ đầu của hai từ cuối trong tên; không có tên thì lấy email.
export function initials(name: string, email: string) {
  const w = name.trim().split(/\s+/).filter(Boolean)
  const src = w.length ? (w.length > 1 ? w[w.length - 2][0] + w[w.length - 1][0] : w[0].slice(0, 2)) : email.slice(0, 2)
  return src.toLocaleUpperCase('vi-VN')
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-[3px] break-words">
      <dt className="text-[13px] font-extrabold leading-[18px] text-ink-secondary">{label}</dt>
      <dd className="text-base font-semibold leading-[22px] text-ink">{children}</dd>
    </div>
  )
}

function GoalCard({ me }: { me: Me }) {
  const target = useGoalTarget() // D-80: cùng nguồn với màn /muc-tieu
  return (
    <section aria-labelledby="goal-title" className={card}>
      <h2 id="goal-title" className="text-xl font-black leading-[27px] text-ink">Mục tiêu học tập</h2>
      <dl className="flex flex-col gap-2.5">
        <Row label="Khóa học"><GoalCourse /></Row>
        <Row label="Band mục tiêu">
          {target.isPending ? <Skeleton className="h-5 w-24" /> : target.isError ? DASH : target.band != null ? `IELTS ${target.band.toFixed(1)}` : 'Chưa đặt'}
        </Row>
        {target.date && <Row label="Ngày mục tiêu">{formatDate(target.date)}</Row>}
        <Row label="Trình độ tự đánh giá">{levelLabel(me.selfLevel) ?? 'Chưa chọn'}</Row>
      </dl>
      <Link to="/muc-tieu" className={`${outline} lg:self-start`}>Chỉnh sửa mục tiêu</Link>
    </section>
  )
}

function ProgressCard() {
  const stats = useMeStats()
  const val = (v?: string) => (stats.isPending ? <Skeleton className="h-[27px] w-16" /> : stats.isError || !v ? DASH : v)
  const s = stats.data
  const items = [
    ['Tổng XP', val(s?.xp.toLocaleString('vi-VN'))],
    ['Chuỗi hiện tại', val(s && `${s.streakCurrent} ngày`)],
  ] as const
  return (
    <section aria-labelledby="progress-title" className={card}>
      <div className="flex items-center gap-2">
        <h2 id="progress-title" className="flex-1 text-xl font-black leading-[27px] text-ink">Tiến độ học tập</h2>
        <img src={cam} alt="" className="size-[54px] shrink-0 object-contain lg:size-16" />
      </div>
      <dl className="flex gap-2">
        {items.map(([label, v]) => (
          <div key={label} className="flex min-w-0 flex-1 flex-col-reverse justify-end gap-[3px] rounded-sm bg-subtle p-2.5">
            <dt className="text-[13px] font-extrabold leading-[18px] text-ink-secondary">{label}</dt>
            <dd className="text-xl font-black leading-[27px] text-brand-strong">{v}</dd>
          </div>
        ))}
      </dl>
      <Link to="/chuoi-hoc" className={`${outline} lg:self-start`}>Xem chuỗi học</Link>
    </section>
  )
}

interface Props {
  me: Me
  saved: boolean
  onEdit: () => void
  onLogout: () => void
}

export function Overview({ me, saved, onEdit, onLogout }: Props) {
  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-[28px] font-black leading-[38px] text-ink lg:text-4xl lg:leading-[49px]">Hồ sơ</h1>
        <p className="text-[15px] font-semibold text-ink-secondary">Thông tin tài khoản và mục tiêu học tập.</p>
      </header>
      {saved && (
        <p role="status" className="rounded-sm bg-success-bg p-3 text-[15px] font-extrabold text-success-fg">Đã lưu thông tin hồ sơ.</p>
      )}
      <section aria-label="Tài khoản" className="flex flex-col gap-3 rounded-lg border border-line-subtle bg-[#eaf1f4] p-4 lg:flex-row lg:items-center lg:rounded-[28px] lg:p-6">
        <div className="flex min-w-0 flex-1 items-center gap-3">
          <span aria-hidden="true" className="grid size-[52px] shrink-0 place-items-center rounded-[28px] border border-line bg-surface text-lg font-black text-brand-strong lg:size-16 lg:text-[22px]">
            {initials(me.displayName, me.email)}
          </span>
          <div className="min-w-0 flex-1 break-words">
            <p className="text-[22px] font-black leading-[30px] text-ink lg:text-[28px] lg:leading-[38px]">{me.displayName}</p>
            <p className="text-[15px] font-semibold text-ink-secondary">{me.email}</p>
          </div>
        </div>
        <button type="button" onClick={onEdit} className={`${outline} rounded-[14px]`}>Chỉnh sửa hồ sơ</button>
      </section>
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:gap-5">
        <GoalCard me={me} />
        <ProgressCard />
      </div>
      <div className="flex flex-col gap-2 lg:flex-row">
        <button type="button" onClick={onLogout} className={`${outline} rounded-[14px] text-danger`}>Đăng xuất</button>
        <Link to="/don-hang" className={outline}>Đơn hàng</Link>
      </div>
    </div>
  )
}
