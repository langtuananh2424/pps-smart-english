import type { ReactNode } from 'react'
import { Link } from 'react-router'
import cam from '../../assets/cam-chao.png'
import { formatDays, formatVnd } from '../checkout/format'
import { chip, chipTone } from '../lectures/ui'
import { bandRange } from './format'
import type { CourseDetail } from './types'

export const cardCls = 'flex flex-col gap-3 rounded-lg border-2 border-line bg-surface p-5 shadow-edge-card'
export const tabsAll = [
  { id: 'overview', label: 'Tổng quan' },
  { id: 'path', label: 'Lộ trình' },
  { id: 'tests', label: 'Bài kiểm tra' },
] as const
export type TabId = (typeof tabsAll)[number]['id']

export const modeLabel = (d: CourseDetail) => (d.deliveryMode === 'CLASS_BASED' ? 'Có lớp' : 'Tự học')

export function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-line-subtle py-2.5 last:border-0">
      <dt className="font-extrabold text-ink">{label}</dt>
      <dd className="text-right font-semibold text-ink-secondary">{children}</dd>
    </div>
  )
}

// Thẻ cam: band đầu vào → đầu ra và mô tả khóa.
export function BandCard({ course }: { course: CourseDetail }) {
  return (
    <section className="flex items-center gap-4 rounded-lg bg-brand-soft p-5">
      <img src={cam} alt="" className="size-20 shrink-0 object-contain" />
      <div className="flex min-w-0 flex-col gap-1">
        <h2 className="text-xl font-black text-brand-ink">Band đầu vào {bandRange(course.bandFrom, course.bandTo)}</h2>
        <p className="font-semibold text-ink-secondary">{course.description}</p>
      </div>
    </section>
  )
}

export function HowToLearn() {
  return (
    <section className={cardCls}>
      <h2 className="text-lg font-black text-ink">Cách học</h2>
      <p className="font-semibold text-ink-secondary">Học ngay phần tự học sau khi mua. Giáo vụ xếp lớp và gửi lịch qua email hoặc thông báo.</p>
    </section>
  )
}

// Bảng lộ trình tóm tắt cho người chưa mua (không bịa dữ liệu: dòng đầu chỉ ghi "Học thử" khi khóa có topic học thử).
export function RoadmapSummary({ course }: { course: CourseDetail }) {
  const trial = course.trialLessonCount > 0 ? 'Học thử' : 'Mở khi mua khóa'
  const rows: [string, string][] =
    course.deliveryMode === 'CLASS_BASED'
      ? [['Topic đầu tiên', trial], ['Các Topic còn lại', 'Mở khi mua khóa'], ['Bài kiểm tra', 'Theo tiến độ khóa']]
      : [
          [[course.levels[0]?.title, course.levels[0]?.topics[0]?.title].filter(Boolean).join(' · ') || 'Topic đầu tiên', trial],
          ['Topic tiếp theo', 'Mở khi mua khóa'],
          ['Kiểm tra chương', 'Dành cho học viên đã mua'],
          ['Kiểm tra cuối khóa', 'Mở khi đạt tiến độ yêu cầu'],
        ]
  return (
    <section className={cardCls}>
      <h2 className="text-lg font-black text-ink">Lộ trình</h2>
      <dl>
        {rows.map(([k, v]) => (
          <Row key={k} label={k}>
            {v}
          </Row>
        ))}
      </dl>
    </section>
  )
}

// Tab "Lộ trình": toàn bộ level và topic của khóa.
export function PathList({ course }: { course: CourseDetail }) {
  return (
    <div className="flex flex-col gap-4">
      {course.levels.map((lv) => (
        <section key={lv.id} className={cardCls}>
          <h2 className="text-lg font-black text-ink">{lv.title}</h2>
          <ul>
            {lv.topics.map((t) => (
              <li key={t.id} className="flex items-center justify-between gap-3 border-b border-line-subtle py-2.5 last:border-0">
                <span className="font-bold text-ink">{t.title}</span>
                <span className="flex shrink-0 items-center gap-2 text-sm font-semibold text-ink-secondary">
                  {t.isFreeTrial && <span className={`${chip} ${chipTone.success}`}>Học thử</span>}
                  {t.lessons.length} bài
                </span>
              </li>
            ))}
          </ul>
        </section>
      ))}
      {!course.levels.length && <p className="font-semibold text-ink-secondary">Khóa học chưa có lộ trình.</p>}
    </div>
  )
}

// Tab "Bài kiểm tra" cho người chưa mua: chỉ mô tả.
export function TestsInfo() {
  return (
    <section className={cardCls}>
      <h2 className="text-lg font-black text-ink">Bài kiểm tra</h2>
      <dl>
        <Row label="Kiểm tra chương">Dành cho học viên đã mua</Row>
        <Row label="Kiểm tra cuối khóa">Mở khi đạt tiến độ yêu cầu</Row>
      </dl>
    </section>
  )
}

interface PackageProps {
  products: CourseDetail['products']
  pickedId: number
  onPick: (id: number) => void
}

// Chọn gói theo thời hạn; mặc định gói ngắn nhất (như trang thanh toán).
export function PackagePicker({ products, pickedId, onPick }: PackageProps) {
  return (
    <fieldset className={cardCls}>
      <legend className="sr-only">Thời hạn</legend>
      <h2 className="text-lg font-black text-ink" aria-hidden="true">
        Thời hạn
      </h2>
      {products.map((p) => (
        <label key={p.id} className={`flex min-h-12 cursor-pointer items-center gap-3 rounded-sm border px-3 py-2 ${p.id === pickedId ? 'border-brand bg-subtle' : 'border-line'}`}>
          <input type="radio" name="product" checked={p.id === pickedId} onChange={() => onPick(p.id)} className="size-4 accent-brand" />
          <span className="flex-1 font-extrabold text-ink">{p.name || formatDays(p.accessDays)}</span>
          <span className="text-sm font-semibold text-ink-secondary">{formatDays(p.accessDays)}</span>
        </label>
      ))}
    </fieldset>
  )
}

export function PackageInfo({ product, classBased }: { product: CourseDetail['products'][number]; classBased: boolean }) {
  return (
    <section className={cardCls}>
      <h2 className="text-lg font-black text-ink">Thông tin gói</h2>
      <p className="text-2xl font-black text-brand-strong">{formatVnd(product.priceVnd)}</p>
      {classBased && <p className="text-sm font-semibold text-ink-secondary">Giá và lượt AI tuần theo gói đã chọn.</p>}
    </section>
  )
}

export function BuyLink({ slug, productId, label, className }: { slug: string; productId?: number; label: string; className: string }) {
  return (
    <Link to={`/thanh-toan?khoa=${slug}${productId ? `&goi=${productId}` : ''}`} className={className}>
      {label}
    </Link>
  )
}
