import { useState } from 'react'
import { Link } from 'react-router'
import { BlockError, Skeleton } from '../home/BlockState'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { useMyAssignments } from './api'
import type { Assignment } from './types'
import { detailPath, formatDue, isTodo, statusOf, titleOf, typeOf } from './view'

type Tab = 'todo' | 'done'
const tabCls = (on: boolean) =>
  `min-h-10 rounded-md border-2 px-4 text-sm font-extrabold ${on ? 'border-brand bg-subtle text-brand-strong' : 'border-line bg-surface text-ink-secondary'}`

function StatusText({ a }: { a: Assignment }) {
  const s = statusOf(a)
  return s.tone === 'danger' ? <span className="inline-flex rounded-full bg-danger-bg px-2.5 py-[3px] text-xs font-extrabold text-danger-fg">{s.label}</span> : <span>{s.label}</span>
}

// FR-ASG-02 (Figma 480:10697): bài giáo viên giao, chia Cần làm / Đã nộp; bảng ở desktop, thẻ ở mobile.
export function AssignmentsPage() {
  const q = useMyAssignments()
  const [tab, setTab] = useState<Tab>('todo')
  const rows = (q.data ?? []).filter((a) => isTodo(a.myStatus) === (tab === 'todo')).sort((a, b) => a.dueAt.localeCompare(b.dueAt))
  const open = (a: Assignment, i: number) => (
    <Link to={detailPath(a)} className={`${tab === 'todo' && i === 0 ? pillPrimary : pillNeutral} !h-10 !px-5 text-sm`}>Mở bài</Link>
  )
  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-[28px] font-black leading-9 text-ink">Bài được giao</h1>
        <p className="font-semibold text-ink-secondary">Xem hạn của từng bài giáo viên giao.</p>
      </header>
      <div role="tablist" className="flex gap-3">
        <button role="tab" aria-selected={tab === 'todo'} className={tabCls(tab === 'todo')} onClick={() => setTab('todo')}>Cần làm</button>
        <button role="tab" aria-selected={tab === 'done'} className={tabCls(tab === 'done')} onClick={() => setTab('done')}>Đã nộp</button>
      </div>
      {q.isError ? <BlockError onRetry={() => void q.refetch()} /> : q.isPending ? <Skeleton className="h-48" /> : (
        <section className="rounded-lg border border-line-brand p-4">
          <h2 className="mb-3 text-lg font-black text-ink">{tab === 'todo' ? 'Bài cần làm' : 'Bài đã nộp'}</h2>
          {rows.length === 0 ? <p className="py-6 text-center font-semibold text-ink-secondary">{tab === 'todo' ? 'Chưa có bài nào cần làm.' : 'Chưa có bài nào đã nộp.'}</p> : (
            <>
              <div aria-hidden className="hidden grid-cols-[2fr_2fr_1fr_1fr] rounded-md bg-muted px-3 py-2.5 text-xs font-extrabold text-ink-secondary md:grid"><span>Bài</span><span>Hạn nộp</span><span>Trạng thái</span><span className="text-right">Thao tác</span></div>
              <ul>{rows.map((a, i) => (
                <li key={a.id} className="flex flex-col items-start gap-1 border-b border-line px-3 py-3 text-sm font-semibold text-ink-secondary last:border-0 md:grid md:grid-cols-[2fr_2fr_1fr_1fr] md:items-center md:py-4">
                  <strong className="font-extrabold text-ink">{titleOf(a)}</strong>
                  <span><span className="md:hidden">Hạn nộp · </span>{typeOf(a)} · {formatDue(a.dueAt)}</span>
                  <span><span className="md:hidden">Trạng thái · </span><StatusText a={a} /></span>
                  <span className="md:justify-self-end">{open(a, i)}</span>
                </li>
              ))}</ul>
            </>
          )}
        </section>
      )}
    </div>
  )
}
