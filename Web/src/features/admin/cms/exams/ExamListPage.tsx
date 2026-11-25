import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { btnOutline, btnPrimary, headCls, Loading, PageHead, RetryError, rowCls, Section } from '../../ui'
import { examApi, useCourseTree, useCourses, useExamWrite, usePlacementBanks, useTopicBanks, type Bank } from './api'
import { bankPath, ErrorText, Field, STATUS_LABEL } from './ui'

type Filter = 'ALL' | 'PLACEMENT' | 'COURSE'
const FILTERS: [Filter, string][] = [
  ['ALL', 'Tất cả'],
  ['PLACEMENT', 'Đầu vào'],
  ['COURSE', 'Trong khóa'],
]

function BankRows({ banks, kind, topicNames }: { banks: Bank[]; kind: string; topicNames?: Map<number, string> }) {
  if (!banks.length) return <p className="font-semibold text-ink-secondary">Chưa có đề nào.</p>
  return (
    <>
      <div className={headCls}>
        <span>Tên đề</span>
        <span>Loại</span>
        <span>Trạng thái</span>
      </div>
      <ul>
        {banks.map((b) => (
          <li key={b.id} className={rowCls}>
            <Link to={bankPath(b.id)} className="min-h-11 break-words py-2.5 font-extrabold text-ink hover:text-brand-strong md:py-0">
              {b.title}
            </Link>
            <span className="font-semibold text-ink-secondary">
              {kind}
              {b.topicId && topicNames?.get(b.topicId) ? ` · ${topicNames.get(b.topicId)}` : ''}
            </span>
            <span className="font-semibold text-ink-secondary">{STATUS_LABEL[b.status] ?? b.status}</span>
          </li>
        ))}
      </ul>
    </>
  )
}

function CreatePlacement() {
  const [title, setTitle] = useState('')
  const [minutes, setMinutes] = useState('')
  const create = useExamWrite(examApi.createPlacement)
  const mins = Number(minutes)
  const valid = title.trim() !== '' && mins > 0
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!valid) return
    create.mutate(
      { title: title.trim(), timeLimitSec: Math.round(mins * 60) },
      {
        onSuccess: () => {
          setTitle('')
          setMinutes('')
        },
      },
    )
  }
  return (
    <Section title="Tạo đề đầu vào">
      <form onSubmit={submit} className="flex flex-col gap-3">
        <div className="flex flex-col gap-3 md:flex-row">
          <Field label="Tên đề" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
          <Field label="Thời gian làm bài (phút)" type="number" min={1} step="any" value={minutes} onChange={(e) => setMinutes(e.target.value)} />
        </div>
        <ErrorText error={create.error} />
        <button type="submit" disabled={!valid || create.isPending} className={`${btnPrimary} self-start`}>
          Tạo đề
        </button>
      </form>
    </Section>
  )
}

function CourseExams() {
  const courses = useCourses()
  const [courseId, setCourseId] = useState('')
  const tree = useCourseTree(courseId)
  const topics = tree.data?.levels.flatMap((l) => l.topics.map((t) => t.topic)) ?? []
  const exams = useTopicBanks(topics.map((t) => t.id))
  return (
    <Section title="Đề trong khóa">
      {courses.isError ? (
        <RetryError error={courses.error} onRetry={() => courses.refetch()} />
      ) : (
        <label className="flex flex-col gap-2">
          <span className="text-sm font-extrabold text-ink">Khóa học</span>
          <select
            value={courseId}
            onChange={(e) => setCourseId(e.target.value)}
            className="min-h-[52px] rounded-md border-[1.5px] border-line-input bg-input px-4 font-bold text-ink"
          >
            <option value="">Chọn khóa học</option>
            {courses.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </label>
      )}
      {courseId &&
        (tree.isPending || exams.pending ? (
          <Loading />
        ) : tree.isError || exams.error ? (
          <RetryError error={tree.error ?? exams.error} onRetry={() => tree.refetch()} />
        ) : (
          <BankRows banks={exams.banks} kind="Trong khóa" topicNames={new Map(topics.map((t) => [t.id, t.title]))} />
        ))}
      <p className="text-sm font-semibold text-ink-secondary">Đề thi khóa là bộ ở chế độ EXAM của từng topic; tạo bộ mới ở màn Ngân hàng câu hỏi.</p>
    </Section>
  )
}

// FR-ADM-06 (Figma 482:16872): danh sách đề đầu vào và đề thi khóa.
export function ExamListPage() {
  const [filter, setFilter] = useState<Filter>('ALL')
  const placement = usePlacementBanks()
  return (
    <>
      <PageHead title="Quản lý đề thi" sub="Tạo và quản lý đề đầu vào, đề trong khóa." />
      <div role="group" aria-label="Lọc loại đề" className="flex flex-wrap gap-2">
        {FILTERS.map(([key, label]) => (
          <button
            key={key}
            type="button"
            aria-pressed={filter === key}
            onClick={() => setFilter(key)}
            className={`inline-flex min-h-12 items-center rounded-md border px-3.5 font-extrabold ${filter === key ? 'border-brand-strong bg-subtle text-ink' : 'border-line-input bg-input text-ink'}`}
          >
            {label}
          </button>
        ))}
      </div>
      {filter !== 'COURSE' && (
        <>
          <Section title="Đề đầu vào">
            {placement.isPending ? (
              <Loading />
            ) : placement.isError ? (
              <RetryError error={placement.error} onRetry={() => placement.refetch()} />
            ) : (
              <BankRows banks={placement.data} kind="Đầu vào" />
            )}
            <Link to="/quan-tri/cms/de-thi/quy-doi" className={`${btnOutline} self-start`}>
              Bảng quy đổi điểm và số lần làm lại
            </Link>
          </Section>
          <CreatePlacement />
        </>
      )}
      {filter !== 'PLACEMENT' && <CourseExams />}
    </>
  )
}
