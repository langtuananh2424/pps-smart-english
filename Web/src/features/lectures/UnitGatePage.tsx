import type { ReactNode } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import check from '../../assets/icons/check.svg'
import { Icon } from '../../components/Icon'
import { ApiError } from '../../lib/api'
import { Skeleton } from '../home/BlockState'
import { useMyCourses, useRoadmap } from '../home/api'
import type { Roadmap } from '../home/types'
import { useCatalogDetail, useUnitGate, type UnitGate } from './api'
import { unitEntry } from './status'
import { btnNeutral, btnPrimary, chip } from './ui'

const panel = 'mx-auto mt-6 flex max-w-md flex-col gap-4 rounded-lg border border-line-subtle bg-surface p-6 shadow-edge-card lg:p-8'

function Panel({ title, sub, children }: { title: string; sub?: string; children: ReactNode }) {
  return (
    <section className={panel}>
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-black leading-[30px] text-ink">{title}</h1>
        {sub && <p className="font-semibold text-ink-secondary">{sub}</p>}
      </div>
      {children}
    </section>
  )
}

// Hàng điều kiện chỉ đọc: ô tick khi đạt.
function Rule({ done, children }: { done: boolean; children: ReactNode }) {
  return (
    <li className="flex items-center gap-3 font-bold text-ink">
      <span className={`grid size-6 shrink-0 place-items-center rounded-[6px] border-2 ${done ? 'border-brand bg-brand text-white' : 'border-line bg-surface'}`}>
        {done && <Icon src={check} size={14} />}
      </span>
      <span>{children}</span>
      <span className="sr-only">{done ? '(đã đạt)' : '(chưa đạt)'}</span>
    </li>
  )
}

function Locked({ gate, target }: { gate: UnitGate; target: string }) {
  const nav = useNavigate()
  const { lectures: l, exercises: e, chapterTest: t } = gate
  return (
    <Panel title="Điều kiện mở Unit" sub="Hoàn thành Unit trước để mở.">
      <ul className="flex flex-col gap-3">
        {l && (
          <Rule done={l.completed >= l.total}>
            Xem bài giảng đạt {l.requiredPercent}% · Hiện tại {l.currentPercent}/{l.requiredPercent}
          </Rule>
        )}
        {e && <Rule done={e.completed >= e.total}>Làm xong bài tập</Rule>}
        {t && <Rule done={t.submitted}>Nộp kiểm tra chương</Rule>}
      </ul>
      <div className="flex flex-wrap gap-3">
        <Link to={target} className={btnPrimary}>
          Học phần còn thiếu
        </Link>
        <button type="button" onClick={() => nav(-1)} className={btnNeutral}>
          Đóng
        </button>
      </div>
    </Panel>
  )
}

function Early({ target }: { target: string }) {
  return (
    <Panel title="Unit đã mở" sub="Giáo viên đã cho phép bạn học Unit này trước.">
      <span className={`${chip} bg-reward-bg text-reward-fg`}>Được mở sớm</span>
      <Link to={target} className={`${btnPrimary} self-start`}>
        Vào Unit
      </Link>
    </Panel>
  )
}

// Khóa không bật tuần tự: danh sách Unit tự chọn thứ tự học.
function FreeOrder({ roadmap, courseId }: { roadmap: Roadmap; courseId: number }) {
  return (
    <Panel title="Lộ trình khóa học" sub="Khóa này cho phép học các Unit theo thứ tự bạn chọn.">
      <ul className="flex flex-col gap-2">
        {roadmap.levels.flatMap((lv) => lv.topics).map((t, _i, all) => (
          <li key={t.id} className="flex items-center justify-between gap-3 rounded-sm border border-line px-3 py-2">
            <span className="font-extrabold text-ink">{t.title}</span>
            <Link to={unitEntry(all, t.id, courseId)} className={btnNeutral}>
              Mở Unit
            </Link>
          </li>
        ))}
      </ul>
    </Panel>
  )
}

// Unit đã mở thường: vào thẳng, trừ khóa không tuần tự (sequential lấy từ catalog theo slug của khóa).
function Open({ roadmap, courseId, topicId }: { roadmap: Roadmap; courseId: number; topicId: number }) {
  const courses = useMyCourses()
  const slug = courses.data?.find((c) => c.courseId === courseId)?.slug
  const detail = useCatalogDetail(slug, true)
  if (courses.isPending || (slug && detail.isPending)) return <Skeleton className="mx-auto mt-6 h-40 max-w-md" />
  if (detail.data?.sequential === false) return <FreeOrder roadmap={roadmap} courseId={courseId} />
  return <Navigate replace to={unitEntry(roadmap.levels.flatMap((l) => l.topics), topicId, courseId)} />
}

function Notice({ title, body }: { title: string; body: string }) {
  return (
    <Panel title={title} sub={body}>
      <Link to="/bai-giang" className={`${btnNeutral} self-start`}>
        Về Bài giảng
      </Link>
    </Panel>
  )
}

// FR-LMS-04: màn điều kiện mở Unit (học tuần tự).
export function UnitGatePage() {
  const p = useParams()
  const courseId = Number(p.courseId)
  const topicId = Number(p.topicId)
  const gate = useUnitGate(courseId, topicId)
  const roadmap = useRoadmap(courseId)

  if (gate.isError) {
    const missing = gate.error instanceof ApiError && gate.error.status === 404
    return <Notice title={missing ? 'Không tìm thấy Unit' : 'Không tải được điều kiện mở Unit'} body={missing ? 'Unit không tồn tại hoặc bạn chưa có khóa học này.' : 'Kiểm tra kết nối rồi thử lại.'} />
  }
  if (gate.isPending) return <Skeleton className="mx-auto mt-6 h-64 max-w-md" />

  const g = gate.data
  const topics = roadmap.data?.levels.flatMap((l) => l.topics) ?? []
  // Unit chặn: bài chưa xong đầu tiên của nó; roadmap lỗi thì về danh sách bài.
  if (g.openedEarly) return <Early target={unitEntry(topics, topicId, courseId)} />
  if (g.locked) return <Locked gate={g} target={unitEntry(topics, g.blockingTopicId, courseId)} />
  if (roadmap.isPending) return <Skeleton className="mx-auto mt-6 h-40 max-w-md" />
  if (!roadmap.data) return <Navigate replace to={`/bai-giang/khoa/${courseId}`} />
  return <Open roadmap={roadmap.data} courseId={courseId} topicId={topicId} />
}
