import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { btnOutline, btnPrimary, Loading, PageHead, RetryError, WriteError } from '../../ui'
import { bankWrites, useBanks, useCmsWrite, useCourses, useCourseTree } from './api'
import { BankForm } from './BankForm'
import { Pill, smBtn, smInput } from './fields'
import type { BankView } from './types'

const MODE = { PRACTICE: 'Luyện tập', EXAM: 'Thi' }

function BankRow({ b, onEdit }: { b: BankView; onEdit: () => void }) {
  const toggle = useCmsWrite(() => bankWrites.setPublished(b.id, b.status !== 'PUBLISHED'))
  const remove = useCmsWrite(() => bankWrites.remove(b.id))
  const live = b.status === 'PUBLISHED'
  return (
    <li className="flex flex-col gap-2 border-b border-line-brand py-3 last:border-b-0">
      <div className="flex flex-wrap items-center gap-3">
        <div className="min-w-0 flex-1">
          <p className="font-extrabold text-ink">{b.title}</p>
          <p className="text-sm font-semibold text-ink-secondary">
            {MODE[b.mode]} · {b.questionCount} câu{b.isTrial ? ' · Học thử' : ''}
          </p>
        </div>
        <Pill tone={live ? 'live' : 'draft'}>{live ? 'Đã xuất bản' : 'Bản nháp'}</Pill>
        <Link to={`/quan-tri/cms/ngan-hang/${b.id}`} className={btnOutline}>
          Chỉnh sửa
        </Link>
        <button type="button" className={smBtn} onClick={onEdit}>
          Thuộc tính
        </button>
        <button type="button" className={smBtn} disabled={toggle.isPending} onClick={() => toggle.mutate()}>
          {live ? 'Ngừng xuất bản' : 'Xuất bản'}
        </button>
        <button
          type="button"
          className={smBtn}
          aria-label={`Xóa bộ ${b.title}`}
          onClick={() => window.confirm(`Xóa bộ "${b.title}"?`) && remove.mutate()}
        >
          Xóa
        </button>
      </div>
      {toggle.isError && <WriteError error={toggle.error} />}
      {remove.isError && <WriteError error={remove.error} />}
    </li>
  )
}

// FR-ADM-02: chọn khóa, topic rồi quản lý các bộ câu hỏi của topic đó.
export function BankListPage() {
  const [params, setParams] = useSearchParams()
  const course = params.get('course') ?? ''
  const topic = params.get('topic') ?? ''
  const [form, setForm] = useState<'new' | BankView | null>(null)
  const [q, setQ] = useState('')
  const courses = useCourses()
  const tree = useCourseTree(course)
  const banks = useBanks(topic)

  const topics = useMemo(() => tree.data?.levels.flatMap((l) => l.topics.map((t) => ({ ...t.topic, level: l.level.title, lessons: t.lessons }))) ?? [], [tree.data])
  const current = topics.find((t) => String(t.id) === topic)
  const shown = banks.data?.filter((b) => b.title.toLowerCase().includes(q.trim().toLowerCase()))
  const pick = (next: Record<string, string>) => {
    setForm(null)
    setParams(next)
  }

  return (
    <div className="flex flex-col gap-5">
      <PageHead title="Ngân hàng câu hỏi" sub="Tìm và biên tập câu hỏi theo topic." />
      {courses.isError && <RetryError error={courses.error} onRetry={() => courses.refetch()} />}
      <div className="grid gap-3 md:grid-cols-2">
        <label className="flex flex-col gap-1.5 text-sm font-extrabold text-ink">
          Khóa học
          <select value={course} onChange={(e) => pick(e.target.value ? { course: e.target.value } : {})} className={smInput}>
            <option value="">Chọn khóa học</option>
            {courses.data?.data.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1.5 text-sm font-extrabold text-ink">
          Topic
          <select value={topic} disabled={!course || tree.isPending} onChange={(e) => pick({ course, ...(e.target.value ? { topic: e.target.value } : {}) })} className={smInput}>
            <option value="">Chọn topic</option>
            {topics.map((t) => (
              <option key={t.id} value={t.id}>
                {t.level} › {t.title}
              </option>
            ))}
          </select>
        </label>
      </div>
      {tree.isError && <RetryError error={tree.error} onRetry={() => tree.refetch()} />}

      {topic && (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <input aria-label="Tìm bộ câu hỏi" placeholder="Tìm theo tên bộ" value={q} onChange={(e) => setQ(e.target.value)} className={`${smInput} max-w-sm`} />
            <button type="button" className={btnPrimary} onClick={() => setForm('new')}>
              Tạo bộ câu hỏi
            </button>
          </div>
          {form && (
            <BankForm
              key={form === 'new' ? 'new' : form.id}
              bank={form === 'new' ? undefined : form}
              topicId={Number(topic)}
              lessons={current?.lessons}
              onDone={() => setForm(null)}
              onCancel={() => setForm(null)}
            />
          )}
          <section className="rounded-lg border border-line-brand bg-surface px-5 py-2 shadow-edge-card">
            {banks.isPending ? (
              <Loading />
            ) : banks.isError ? (
              <RetryError error={banks.error} onRetry={() => banks.refetch()} />
            ) : shown?.length ? (
              <ul>
                {shown.map((b) => (
                  <BankRow key={b.id} b={b} onEdit={() => setForm(b)} />
                ))}
              </ul>
            ) : (
              <p className="py-3 font-bold text-ink-secondary">Chưa có bộ câu hỏi nào.</p>
            )}
          </section>
        </>
      )}
    </div>
  )
}
