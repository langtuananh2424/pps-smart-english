import { useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import { labelOf } from '../exercises/format'
import { BlockError, Skeleton } from '../home/BlockState'
import { useLearningProfile, type LearningProfile, type ProfileMetric } from './api'

const card = 'flex min-w-0 flex-col gap-3 rounded-lg border border-line bg-surface p-4 lg:p-6'
const h2 = 'text-xl font-black leading-[27px] text-ink'
const btn = 'flex h-12 items-center justify-center rounded-sm border border-line bg-surface px-4 text-[15px] font-extrabold text-brand-strong'
const pct = (v: number | null) => (v == null ? '—' : `${v}%`)
const num = (n: number) => n.toLocaleString('vi-VN')

function Bar({ value, color = 'bg-brand-strong' }: { value: number | null; color?: string }) {
  return (
    <div className="h-2.5 min-w-0 flex-1 overflow-hidden rounded-full bg-muted">
      <div className={`h-full rounded-full ${color}`} style={{ width: `${value ?? 0}%` }} />
    </div>
  )
}

function Overview({ p }: { p: LearningProfile }) {
  // aiGraded nằm trong ungraded (đã có điểm Cam) nên phần đang chấm là hiệu.
  const waiting = p.bySkill.reduce((s, m) => s + m.ungraded - m.aiGraded, 0)
  const items = [['Câu đã làm', num(p.answered)], ['Tỉ lệ đúng', pct(p.accuracy)], ['Câu Cam đang chấm', num(waiting)]]
  return (
    <section aria-labelledby="lp-overview" className={card}>
      <h2 id="lp-overview" className={h2}>Tổng quan</h2>
      <dl className="flex flex-col gap-2 sm:flex-row">
        {items.map(([label, v]) => (
          <div key={label} className="flex min-w-0 flex-1 flex-col-reverse justify-end gap-[3px] rounded-sm bg-subtle p-2.5">
            <dt className="text-[13px] font-extrabold leading-[18px] text-ink-secondary">{label}</dt>
            <dd className="text-xl font-black leading-[27px] text-brand-strong">{v}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

function Improvements({ list }: { list: LearningProfile['improvements'] }) {
  return (
    <section aria-labelledby="lp-improve" className={`${card} lg:flex-1`}>
      <h2 id="lp-improve" className={h2}>Cần cải thiện</h2>
      {list.length === 0 ? (
        <p className="text-[15px] font-semibold text-ink-secondary">Chưa đủ dữ liệu, hãy làm thêm câu ở mỗi phần để Cam gợi ý.</p>
      ) : (
        <>
          <p className="text-[13px] font-semibold text-ink-secondary">Ba phần có tỉ lệ đúng thấp nhất trong các phần bạn đã làm đủ nhiều câu.</p>
          <ul className="flex flex-col gap-2.5">
            {list.map((i) => (
              <li key={`${i.dimension}-${i.key}`} className="flex flex-col gap-1">
                <div className="flex justify-between gap-2 text-[15px] font-bold text-ink">
                  <span className="min-w-0 break-words">{i.label}</span>
                  <span>{i.accuracy}%</span>
                </div>
                <Bar value={i.accuracy} color="bg-danger" />
                <span className="text-xs font-semibold text-ink-secondary">{num(i.answered)} câu đã làm</span>
              </li>
            ))}
          </ul>
          <Link to="/bai-tap" className={`${btn} lg:self-start`}>Luyện thêm phần này</Link>
        </>
      )}
    </section>
  )
}

// Cột rỗng khi tuần không học; danh sách sr-only là bản thay thế cho người dùng đọc màn hình.
function Trend({ weeks }: { weeks: LearningProfile['weeklyTrend'] }) {
  const last = weeks.length - 1
  return (
    <section aria-labelledby="lp-trend" className={`${card} lg:flex-1`}>
      <h2 id="lp-trend" className={h2}>Diễn biến 12 tuần</h2>
      <p className="text-[13px] font-semibold text-ink-secondary">Tỉ lệ đúng mỗi tuần. Tuần không học để trống.</p>
      <ul className="sr-only">
        {weeks.map((w, i) => (
          <li key={w.weekStart}>Tuần {i + 1}: {w.answered === 0 ? 'không học' : `${pct(w.accuracy)}, ${w.answered} câu`}</li>
        ))}
      </ul>
      <div aria-hidden="true" data-testid="trend" className="flex h-44 items-end gap-1 sm:gap-1.5">
        {weeks.map((w, i) => (
          <div key={w.weekStart} className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1">
            <span className="text-[11px] font-extrabold text-ink-secondary">{w.answered > 0 ? w.accuracy ?? '' : ''}</span>
            {/* Cột tối thiểu 2% để tuần có học nhưng 0% vẫn thấy được */}
            <div
              data-testid="week-bar"
              className={`w-full rounded-t-md ${w.answered === 0 ? 'h-0.5 bg-muted' : i === last ? 'bg-brand-strong' : 'bg-[#fdb185]'}`}
              style={w.answered === 0 ? undefined : { height: `${Math.max(w.accuracy ?? 0, 2) * 0.75}%` }}
            />
            <span className="text-[11px] font-extrabold text-ink-secondary">T{i + 1}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

function SkillRow({ m }: { m: ProfileMetric }) {
  const detail =
    m.accuracy != null
      ? `${m.accuracy}% · ${num(m.answered)} câu`
      : m.aiAverageScore != null
        ? `${m.aiAverageScore}%`
        : 'Chưa có câu'
  return (
    <li className="flex flex-col gap-1 sm:flex-row sm:items-center sm:gap-4">
      <span className="text-[15px] font-bold text-ink sm:w-28 sm:shrink-0">{m.label || m.key}</span>
      <Bar value={m.accuracy ?? m.aiAverageScore} />
      <span className="text-xs font-extrabold text-ink-secondary sm:w-56 sm:shrink-0">
        {detail}
        {m.aiGraded > 0 && ` · Cam chấm ${num(m.aiGraded)} bài`}
      </span>
    </li>
  )
}

const TABS = [['byType', 'Dạng bài'], ['byGrammar', 'Ngữ pháp'], ['byTopic', 'Chủ đề']] as const

function Detail({ p }: { p: LearningProfile }) {
  const [tab, setTab] = useState<(typeof TABS)[number][0]>('byType')
  const rows = p[tab]
  const move = (e: KeyboardEvent<HTMLButtonElement>) => {
    const step = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0
    if (!step) return
    e.preventDefault()
    const i = TABS.findIndex(([k]) => k === tab)
    const next = TABS[(i + step + TABS.length) % TABS.length][0]
    setTab(next)
    document.getElementById(`lp-tab-${next}`)?.focus()
  }
  return (
    <section aria-labelledby="lp-detail" className={card}>
      <h2 id="lp-detail" className={h2}>Chi tiết theo phần</h2>
      <div role="tablist" aria-label="Chi tiết theo phần" className="flex flex-wrap gap-2">
        {TABS.map(([k, label]) => (
          <button
            key={k}
            id={`lp-tab-${k}`}
            role="tab"
            type="button"
            aria-selected={tab === k}
            aria-controls="lp-tabpanel"
            tabIndex={tab === k ? 0 : -1}
            onClick={() => setTab(k)}
            onKeyDown={move}
            className={`min-h-11 rounded-full px-4 text-[13px] font-extrabold ${tab === k ? 'bg-brand-strong text-white' : 'bg-subtle text-brand-strong'}`}
          >
            {label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id="lp-tabpanel" aria-labelledby={`lp-tab-${tab}`}>
      <ul className="flex flex-col gap-2">
        {rows.length === 0 && <li className="text-[15px] font-semibold text-ink-secondary">Chưa có dữ liệu.</li>}
        {rows.map((m) => (
          <li key={m.key} className="flex items-center gap-3 text-[15px] text-ink">
            <span className="min-w-0 flex-1 break-words font-semibold">{m.label || (tab === 'byType' ? labelOf(m.key) : m.key)}</span>
            <span className="text-xs font-extrabold text-ink-secondary">{num(m.answered)} câu</span>
            <span className="w-12 text-right font-black text-brand-strong">{pct(m.accuracy)}</span>
          </li>
        ))}
      </ul>
      </div>
    </section>
  )
}

function Body({ p }: { p: LearningProfile }) {
  const skills = p.bySkill.filter((m) => m.key !== 'UNSPECIFIED')
  // Học viên chỉ làm Viết/Nói có answered=0 nhưng vẫn có câu chờ/đã có điểm Cam.
  if (p.answered === 0 && !p.bySkill.some((m) => m.ungraded > 0)) {
    return (
      <section className={card}>
        <p className="text-[15px] font-semibold text-ink">Bạn chưa làm bài nào. Làm vài bài tập để Cam tổng hợp năng lực của bạn.</p>
        <Link to="/bai-tap" className={`${btn} bg-brand-strong text-white lg:self-start`}>Đến bài tập</Link>
      </section>
    )
  }
  return (
    <>
      <Overview p={p} />
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <Improvements list={p.improvements} />
        <Trend weeks={p.weeklyTrend} />
      </div>
      <section aria-labelledby="lp-skill" className={card}>
        <h2 id="lp-skill" className={h2}>Theo kỹ năng</h2>
        {skills.length === 0 ? (
          <p className="text-[15px] font-semibold text-ink-secondary">Chưa có dữ liệu.</p>
        ) : (
          <ul className="flex flex-col gap-3">{skills.map((m) => <SkillRow key={m.key} m={m} />)}</ul>
        )}
      </section>
      <Detail p={p} />
    </>
  )
}

// FR-EX-09, D-139: hồ sơ năng lực của học viên từ GET /api/me/learning-profile.
export function LearningProfilePage() {
  const q = useLearningProfile()
  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-[28px] font-black leading-[38px] text-ink lg:text-4xl lg:leading-[49px]">Hồ sơ năng lực</h1>
        <p className="text-[15px] font-semibold text-ink-secondary">Điểm mạnh, điểm yếu và tiến bộ của bạn, tính từ các bài đã làm.</p>
      </header>
      {q.isError ? <BlockError onRetry={() => q.refetch()} /> : !q.data ? <Skeleton className="h-[320px] w-full rounded-lg" /> : <Body p={q.data} />}
      <Link to="/ho-so" className={`${btn} self-start`}>Về hồ sơ</Link>
    </div>
  )
}
