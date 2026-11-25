import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { ApiError } from '../../lib/api'
import { CourseTests } from '../exercises/CourseTests'
import { Skeleton } from '../home/BlockState'
import { useMyCourses } from '../home/api'
import type { MyCourse } from '../home/types'
import { useCatalogDetail } from '../lectures/api'
import { btnNeutral, btnPrimary, chip, chipTone } from '../lectures/ui'
import { accessOf, formatDate, isExpiring, type Access } from './access'
import { BandCard, BuyLink, cardCls, HowToLearn, modeLabel, PackageInfo, PackagePicker, PathList, RoadmapSummary, Row, tabsAll, TestsInfo, type TabId } from './DetailParts'
import type { CourseDetail } from './types'
import { useTrial, type Trial } from './useTrial'

const centerCard = `${cardCls} mx-auto w-full max-w-xl items-center text-center`

function Missing({ error }: { error?: unknown }) {
  const gone = error instanceof ApiError && error.status === 404
  return (
    <section role="alert" className={centerCard}>
      <h1 className="text-2xl font-black text-ink">{gone || !error ? 'Không tìm thấy khóa học' : 'Không tải được khóa học'}</h1>
      <p className="font-semibold text-ink-secondary">{gone ? 'Khóa học không tồn tại hoặc đã ngừng bán.' : 'Kiểm tra kết nối rồi thử lại.'}</p>
      <Link to="/khoa-hoc" className={btnNeutral}>
        Về khóa học
      </Link>
    </section>
  )
}

// Khách có học thử: đăng nhập để lưu tiến độ (useTrial tự chuyển qua đăng nhập rồi quay lại bài học thử).
function GuestTrialCard({ course, trial, onPath }: { course: CourseDetail; trial: Trial; onPath: () => void }) {
  return (
    <section className={centerCard}>
      <span className={`${chip} ${chipTone.brand}`}>Topic đầu tiên</span>
      <h2 className="text-xl font-black text-ink">Học thử {course.title}</h2>
      <p className="font-bold text-ink">Đăng nhập để lưu tiến độ ở Topic học thử.</p>
      <p className="font-semibold text-ink-secondary">Bạn có thể xem giới thiệu, lộ trình và bài kiểm tra của khóa trước khi đăng nhập.</p>
      <div className="flex flex-wrap justify-center gap-3">
        <button type="button" disabled={trial.busy != null} onClick={() => trial.open(course.slug)} className={btnPrimary}>
          Đăng nhập để học thử
        </button>
        <button type="button" onClick={onPath} className={btnNeutral}>
          Xem lộ trình
        </button>
      </div>
    </section>
  )
}

function ExpiredCard({ course, mine }: { course: CourseDetail; mine: MyCourse }) {
  return (
    <section className={centerCard}>
      <span className={`${chip} bg-danger-bg text-danger-fg`}>Khóa đã hết hạn</span>
      <h2 className="text-xl font-black text-ink">{course.title}</h2>
      <p className="font-bold text-ink">Thời hạn học đã kết thúc.</p>
      <p className="font-semibold text-ink-secondary">Bạn có thể xem lịch sử và kết quả đã có. Gia hạn để học tiếp.</p>
      <div className="flex flex-wrap justify-center gap-3">
        <BuyLink slug={course.slug} label="Gia hạn" className={btnPrimary} />
        <Link to={`/bai-tap/khoa/${mine.courseId}`} className={btnNeutral}>
          Xem lịch sử
        </Link>
      </div>
    </section>
  )
}

function ActiveAside({ course, mine }: { course: CourseDetail; mine: MyCourse }) {
  const renewable = isExpiring(mine.expiresAt)
  return (
    <>
      <section className={cardCls}>
        <h2 className="text-lg font-black text-ink">Quyền học</h2>
        <dl>
          {mine.expiresAt && <Row label="Hết hạn">{formatDate(mine.expiresAt)}</Row>}
          <Row label="Lộ trình">Tiếp tục từ bài đang học</Row>
        </dl>
      </section>
      <Link to={`/bai-giang/khoa/${mine.courseId}`} className={btnPrimary}>
        Vào học
      </Link>
      {renewable && <BuyLink slug={course.slug} label="Gia hạn" className={btnNeutral} />}
    </>
  )
}

function BuyAside({ course, access, trial }: { course: CourseDetail; access: Access; trial: Trial }) {
  const products = [...course.products].sort((a, b) => a.accessDays - b.accessDays)
  const [picked, setPicked] = useState<number | null>(null)
  const product = products.find((p) => p.id === picked) ?? products[0]
  return (
    <>
      {product ? (
        <>
          <PackagePicker products={products} pickedId={product.id} onPick={setPicked} />
          <PackageInfo product={product} classBased={course.deliveryMode === 'CLASS_BASED'} />
          <BuyLink slug={course.slug} productId={product.id} label="Mua khóa" className={btnPrimary} />
        </>
      ) : (
        <p className="font-semibold text-ink-secondary">Khóa học chưa mở bán.</p>
      )}
      {access === 'NONE' && course.trialLessonCount > 0 && (
        <button type="button" disabled={trial.busy != null} onClick={() => trial.open(course.slug)} className={btnNeutral}>
          Học thử
        </button>
      )}
      {trial.failed && (
        <p role="alert" className="text-sm font-bold text-ink-secondary">
          Không mở được bài học thử. Vui lòng thử lại.
        </p>
      )}
    </>
  )
}

function Header({ course, access }: { course: CourseDetail; access: Access }) {
  return (
    <header className="flex flex-col gap-2">
      <nav aria-label="Đường dẫn" className="text-sm font-bold text-ink-muted">
        <Link to="/khoa-hoc">Khóa học</Link>
      </nav>
      <div className="flex flex-wrap gap-2">
        <span className={`${chip} ${chipTone.brand}`}>{modeLabel(course)}</span>
        {access === 'ACTIVE' && <span className={`${chip} ${chipTone.success}`}>Đang học</span>}
      </div>
      <h1 className="text-2xl font-black leading-[30px] text-ink lg:text-[30px] lg:leading-[38px]">{course.title}</h1>
      <p className="font-semibold text-ink-secondary">{access === 'ACTIVE' ? 'Quyền học còn hiệu lực.' : course.description}</p>
    </header>
  )
}

function Tabs({ tab, onTab }: { tab: TabId; onTab: (t: TabId) => void }) {
  return (
    <div role="tablist" aria-label="Nội dung khóa học" className="flex gap-2 overflow-x-auto pb-1">
      {tabsAll.map((t) => (
        <button
          key={t.id}
          type="button"
          role="tab"
          id={`tab-${t.id}`}
          aria-selected={tab === t.id}
          aria-controls="tab-panel"
          onClick={() => onTab(t.id)}
          className={`min-h-11 shrink-0 rounded-full border-2 px-4 text-sm font-extrabold ${tab === t.id ? 'border-brand bg-[#fff8f1] text-brand-strong' : 'border-line bg-surface text-ink-secondary'}`}
        >
          {t.label}
        </button>
      ))}
    </div>
  )
}

function Panel({ tab, course, access }: { tab: TabId; course: CourseDetail; access: Access }) {
  if (tab === 'path') return <PathList course={course} />
  if (tab === 'tests') return access === 'ACTIVE' ? <CourseTests courseId={course.courseId} slug={course.slug} /> : <TestsInfo />
  return (
    <div className="flex flex-col gap-4">
      <BandCard course={course} />
      {course.deliveryMode === 'CLASS_BASED' && <HowToLearn />}
      <RoadmapSummary course={course} />
    </div>
  )
}

function Body({ course, mine, access }: { course: CourseDetail; mine?: MyCourse; access: Access }) {
  const trial = useTrial()
  const [tab, setTab] = useState<TabId>('overview')
  const tabsRef = useRef<HTMLDivElement>(null)
  const showPath = () => {
    setTab('path')
    tabsRef.current?.scrollIntoView?.({ behavior: 'smooth' })
  }
  const expiring = access === 'ACTIVE' && isExpiring(mine?.expiresAt ?? null)

  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <Header course={course} access={access} />
      {expiring && (
        <div role="status" className="rounded-md bg-reward-bg px-4 py-3 font-bold text-reward-fg">
          Khóa sắp hết hạn – Bạn có thể gia hạn khi còn 30 ngày hoặc ít hơn
        </div>
      )}
      {access === 'GUEST' && course.trialLessonCount > 0 && <GuestTrialCard course={course} trial={trial} onPath={showPath} />}
      {access === 'EXPIRED' && mine && <ExpiredCard course={course} mine={mine} />}
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px] lg:gap-6">
        <div ref={tabsRef} className="flex min-w-0 flex-col gap-4">
          <Tabs tab={tab} onTab={setTab} />
          <div id="tab-panel" role="tabpanel" aria-labelledby={`tab-${tab}`}>
            <Panel tab={tab} course={course} access={access} />
          </div>
        </div>
        {access !== 'EXPIRED' && access !== 'UNKNOWN' && (
          <aside aria-label="Mua khóa" className="flex flex-col gap-4 max-lg:order-first">
            {access === 'ACTIVE' && mine ? <ActiveAside course={course} mine={mine} /> : <BuyAside course={course} access={access} trial={trial} />}
          </aside>
        )}
      </div>
    </div>
  )
}

// Chi tiết khóa học (công khai): giao diện theo quyền của người xem.
export function CourseDetailPage() {
  const slug = useParams().slug
  const { user } = useAuth()
  const detail = useCatalogDetail(slug, true)
  const mine = useMyCourses(!!user)

  if (detail.isError) return <Missing error={detail.error} />
  if (detail.isPending || (user && mine.isPending)) return <Skeleton className="h-72 w-full" />
  const entry = mine.data?.find((c) => c.slug === detail.data.slug)
  return <Body key={detail.data.slug} course={detail.data} mine={entry} access={mine.isError ? 'UNKNOWN' : accessOf(!!user, entry)} />
}
