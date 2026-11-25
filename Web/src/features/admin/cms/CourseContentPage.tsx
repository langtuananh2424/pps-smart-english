import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { btnOutline, Loading, PageHead, RetryError, Section, WriteError } from '../ui'
import { cmsWrites, LESSON_TYPES, SKILLS, useCmsWrite, useCourseTree, type LessonView, type LevelNode, type TopicNode } from './api'
import { NodeForm, type FieldDef } from './NodeForm'
import { NodeRow, small } from './NodeRow'
import { CMS, courseUrl, StatusPill } from './ui'

export const lessonFields: FieldDef[] = [
  { name: 'title', label: 'Tên bài giảng', required: true },
  { name: 'type', label: 'Loại bài', kind: 'select', options: LESSON_TYPES, required: true },
  { name: 'videoRef', label: 'Video YouTube (mã hoặc URL)' },
]
const levelFields: FieldDef[] = [
  { name: 'code', label: 'Mã cấp độ', required: true },
  { name: 'title', label: 'Tên cấp độ', required: true },
]
const topicFields: FieldDef[] = [
  { name: 'title', label: 'Tên chủ đề', required: true },
  { name: 'skill', label: 'Kỹ năng', kind: 'select', options: SKILLS },
  { name: 'isFreeTrial', label: 'Học thử miễn phí', kind: 'check' },
]

// Nút "Thêm ..." mở form tạo; lỗi 400/409 hiện trong form.
function AddBox({ label, fields, create }: { label: string; fields: FieldDef[]; create: (b: object) => Promise<unknown> }) {
  const [open, setOpen] = useState(false)
  if (!open)
    return (
      <button type="button" onClick={() => setOpen(true)} className={`${btnOutline} !h-11 self-start`}>
        {label}
      </button>
    )
  const onSubmit = async (b: object) => {
    await create(b)
    setOpen(false)
  }
  return <NodeForm fields={fields} submitLabel={label} onSubmit={onSubmit} onCancel={() => setOpen(false)} />
}

const nest = 'ml-3 flex flex-col gap-2 border-l-2 border-line-brand pl-3'

function LessonLine({ courseId, l }: { courseId: number; l: LessonView }) {
  return (
    <NodeRow
      kind="lessons"
      id={l.id}
      title={l.title}
      status={l.status}
      meta={l.type}
      actions={
        <Link to={`${courseUrl(courseId)}/bai-giang/${l.id}`} className={small}>
          Chỉnh sửa
        </Link>
      }
    />
  )
}

function TopicBlock({ courseId, n }: { courseId: number; n: TopicNode }) {
  const t = n.topic
  const patch = useCmsWrite((b: object) => cmsWrites.patch('topics', t.id, b))
  const add = useCmsWrite((b: object) => cmsWrites.create(`topics/${t.id}`, 'lessons', b))
  const initial = { title: t.title, skill: t.skill ?? '', isFreeTrial: t.isFreeTrial }
  return (
    <NodeRow
      kind="topics"
      id={t.id}
      title={t.title}
      status={t.status}
      meta={[t.skill, t.isFreeTrial ? 'Học thử' : null].filter(Boolean).join(' · ') || undefined}
      editor={(close) => (
        <NodeForm
          fields={topicFields}
          initial={initial}
          submitLabel="Lưu chủ đề"
          onSubmit={async (b) => {
            await patch.mutateAsync(b)
            close()
          }}
          onCancel={close}
        />
      )}
    >
      <div className={nest}>
        {n.lessons.map((l) => (
          <LessonLine key={l.id} courseId={courseId} l={l} />
        ))}
        <AddBox label="Thêm bài giảng" fields={lessonFields} create={add.mutateAsync} />
      </div>
    </NodeRow>
  )
}

function LevelBlock({ courseId, n }: { courseId: number; n: LevelNode }) {
  const lv = n.level
  const patch = useCmsWrite((b: object) => cmsWrites.patch('levels', lv.id, b))
  const add = useCmsWrite((b: object) => cmsWrites.create(`levels/${lv.id}`, 'topics', b))
  return (
    <NodeRow
      kind="levels"
      id={lv.id}
      title={`${lv.code} · ${lv.title}`}
      status={lv.status}
      editor={(close) => (
        <NodeForm
          fields={levelFields}
          initial={{ code: lv.code, title: lv.title }}
          submitLabel="Lưu cấp độ"
          onSubmit={async (b) => {
            await patch.mutateAsync(b)
            close()
          }}
          onCancel={close}
        />
      )}
    >
      <div className={nest}>
        {n.topics.map((t) => (
          <TopicBlock key={t.topic.id} courseId={courseId} n={t} />
        ))}
        <AddBox label="Thêm chủ đề" fields={topicFields} create={add.mutateAsync} />
      </div>
    </NodeRow>
  )
}

// FR-ADM-01: cây Level > Topic > bài học của một khóa (kể cả bản nháp).
export function CourseContentPage() {
  const { courseId = '' } = useParams()
  const q = useCourseTree(courseId)
  const publish = useCmsWrite((p: boolean) => cmsWrites.setPublished('courses', Number(courseId), p))
  const addLevel = useCmsWrite((b: object) => cmsWrites.create(`courses/${Number(courseId)}`, 'levels', b))
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />
  const { course, levels } = q.data
  const published = course.status === 'PUBLISHED'
  return (
    <div className="flex flex-col gap-4">
      <PageHead title={course.title} sub="Nội dung khóa học" />
      <div className="flex flex-wrap items-center gap-2">
        <StatusPill status={course.status} />
        <button type="button" className={small} disabled={publish.isPending} onClick={() => publish.mutate(!published)}>
          {published ? 'Hủy xuất bản khóa' : 'Xuất bản khóa'}
        </button>
        <Link to={`${courseUrl(course.id)}/cau-hinh`} className={small}>
          Cấu hình học và kiểm tra
        </Link>
      </div>
      {publish.isError && <WriteError error={publish.error} onRetry={() => publish.reset()} />}
      <Section title="Cấp độ, chủ đề và bài giảng">
        {levels.length === 0 && <p className="font-bold text-ink-secondary">Khóa học chưa có cấp độ nào.</p>}
        {levels.map((n) => (
          <LevelBlock key={n.level.id} courseId={course.id} n={n} />
        ))}
        <AddBox label="Thêm cấp độ" fields={levelFields} create={addLevel.mutateAsync} />
      </Section>
      <Link to={CMS} className={`${small} self-start`}>
        Về danh sách khóa
      </Link>
    </div>
  )
}
