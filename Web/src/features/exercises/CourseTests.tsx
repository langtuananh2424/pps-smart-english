import { Link } from 'react-router'
import { BlockError, Skeleton } from '../home/BlockState'
import { btnNeutral, btnPrimary } from '../lectures/ui'
import { useCourseTests, useExamStatus } from './api'
import { actionsOf, retakesLabel, statusLines } from './testsView'
import type { CourseTest } from './types'

const grid = 'lg:grid lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1.4fr)_120px_minmax(0,1fr)] lg:items-center lg:gap-4'
const btnSmall = 'min-h-11 px-4 py-2.5 text-sm'

interface TableProps {
  title: string
  heads: [string, string, string, string]
  tests: CourseTest[]
  courseId: number
  slug?: string
  required: number | null
}

function TestTable({ title, heads, tests, courseId, slug, required }: TableProps) {
  return (
    <section aria-label={title} className="rounded-lg border-2 border-line bg-surface p-4 shadow-edge-card lg:p-5">
      <h2 className="mb-3 text-lg font-black text-ink">{title}</h2>
      <div aria-hidden="true" className={`${grid} hidden border-b border-line pb-2 text-sm font-extrabold text-ink-secondary`}>
        {heads.map((h) => (
          <span key={h}>{h}</span>
        ))}
      </div>
      <ul>
        {tests.map((t) => {
          const actions = actionsOf(t, courseId, slug)
          return (
            <li key={`${t.kind}-${t.topicId ?? 'final'}`} className={`${grid} flex flex-col gap-2 border-b border-line-subtle py-3 last:border-0`}>
              <span className="font-extrabold text-ink">{t.title}</span>
              <span className="text-sm font-semibold text-ink-secondary">
                {statusLines(t, required).map((l) => (
                  <span key={l} className="block">
                    {l}
                  </span>
                ))}
              </span>
              <span className="text-sm font-semibold text-ink-secondary">
                <span className="lg:hidden">{heads[2]}: </span>
                {retakesLabel(t)}
              </span>
              <span className="flex flex-wrap gap-2">
                {actions.map((a, i) => (
                  <Link key={a.label} to={a.to} className={i === 0 && !t.lastResult ? `${btnPrimary} ${btnSmall}` : `${btnNeutral} ${btnSmall}`}>
                    {a.label}
                  </Link>
                ))}
                {!actions.length && (
                  <button type="button" disabled className={`${btnNeutral} ${btnSmall} opacity-60`}>
                    Làm bài
                  </button>
                )}
              </span>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

// D-118: kiểm tra chương và kiểm tra cuối khóa của một khóa. slug dùng cho CTA mua khi chưa có khóa.
export function CourseTests({ courseId, slug }: { courseId: number; slug?: string }) {
  const tests = useCourseTests(courseId)
  const exam = useExamStatus(courseId)
  if (tests.isError) return <BlockError onRetry={() => tests.refetch()} />
  if (tests.isPending) return <Skeleton className="h-40 w-full" />
  const required = exam.data?.requiredPercent ?? null
  const chapters = tests.data.filter((t) => t.kind === 'CHAPTER')
  const finals = tests.data.filter((t) => t.kind === 'FINAL')
  return (
    <div className="flex flex-col gap-4">
      {chapters.length > 0 && <TestTable title="Kiểm tra chương" heads={['Bài kiểm tra', 'Trạng thái', 'Số lần làm lại', 'Thao tác']} tests={chapters} courseId={courseId} slug={slug} required={required} />}
      {finals.length > 0 && <TestTable title="Kiểm tra cuối khóa" heads={['Bài kiểm tra', 'Điều kiện mở', 'Số lần làm lại', 'Thao tác']} tests={finals} courseId={courseId} slug={slug} required={required} />}
    </div>
  )
}
