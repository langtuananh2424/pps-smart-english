import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { Loading, PageHead, RetryError, Section, WriteError } from '../ui'
import { cmsWrites, LESSON_TYPES, useCmsWrite, useCourseTree, type CourseTree, type LessonView } from './api'
import { NodeForm, type FieldDef } from './NodeForm'
import { small } from './NodeRow'
import { courseUrl, pillPrimary, StatusPill } from './ui'

const infoFields: FieldDef[] = [
  { name: 'title', label: 'Tiêu đề', required: true },
  { name: 'type', label: 'Loại bài', kind: 'select', options: LESSON_TYPES, required: true },
  { name: 'durationSec', label: 'Thời lượng (giây)', kind: 'number' },
]
const videoFields: FieldDef[] = [{ name: 'videoRef', label: 'Video (mã hoặc URL YouTube)' }]

// Tìm bài giảng trong cây kèm đường dẫn "Khóa / Cấp độ / Chủ đề" cho dòng phụ đề.
function locate(tree: CourseTree, lessonId: string) {
  for (const lv of tree.levels)
    for (const t of lv.topics) {
      const lesson = t.lessons.find((l) => String(l.id) === lessonId)
      if (lesson) return { lesson, path: [tree.course.title, lv.level.title, t.topic.title].join(' / ') }
    }
  return null
}

const notFound = <p role="alert" className="font-bold text-ink-secondary">Không tìm thấy bài giảng.</p>

// FR-ADM-01: chỉnh sửa bài giảng (Figma 369:8154). Hai thẻ gửi PATCH một phần; chưa có mô tả, tải video, phụ đề (xem báo cáo).
export function LessonEditPage() {
  const { courseId = '', lessonId = '' } = useParams()
  const navigate = useNavigate()
  const q = useCourseTree(courseId)
  const patch = useCmsWrite((b: object) => cmsWrites.patch('lessons', Number(lessonId), b))
  const publish = useCmsWrite((p: boolean) => cmsWrites.setPublished('lessons', Number(lessonId), p))
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />
  const found = locate(q.data, lessonId)
  if (!found) return notFound
  const lesson: LessonView = found.lesson
  const published = lesson.status === 'PUBLISHED'
  const toggle = async () => {
    await publish.mutateAsync(!published).then(
      () => !published && navigate(`${courseUrl(courseId)}/bai-giang/${lessonId}/da-xuat-ban`, { state: { published: true } }),
      () => undefined,
    )
  }
  return (
    <div className="flex flex-col gap-4">
      <PageHead title="Chỉnh sửa bài giảng" sub={found.path} />
      <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
        <Section title="Nội dung bài giảng">
          <NodeForm
            key={`i${lesson.id}`}
            fields={infoFields}
            initial={{ title: lesson.title, type: lesson.type, durationSec: lesson.durationSec == null ? '' : String(lesson.durationSec) }}
            submitLabel="Lưu bài giảng"
            onSubmit={(b) => patch.mutateAsync(b)}
          />
        </Section>
        <Section title="Video">
          <NodeForm key={`v${lesson.id}`} fields={videoFields} initial={{ videoRef: lesson.videoRef ?? '' }} submitLabel="Lưu video" onSubmit={(b) => patch.mutateAsync(b)} />
          <div className="flex flex-wrap items-center gap-2">
            <StatusPill status={lesson.status} />
            <button type="button" className={pillPrimary} disabled={publish.isPending} onClick={toggle}>
              {published ? 'Hủy xuất bản bài giảng' : 'Xuất bản bài giảng'}
            </button>
          </div>
          {publish.isError && <WriteError error={publish.error} onRetry={() => publish.reset()} />}
        </Section>
      </div>
      <Link to={courseUrl(courseId)} className={`${small} self-start`}>
        Về nội dung khóa
      </Link>
    </div>
  )
}

// Kết quả sau khi xuất bản (Figma 370:7578).
export function PublishedLessonPage() {
  const { courseId = '', lessonId = '' } = useParams()
  const justPublished = (useLocation().state as { published?: boolean } | null)?.published === true
  const q = useCourseTree(courseId)
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />
  const found = locate(q.data, lessonId)
  if (!found) return notFound
  // Gõ thẳng URL của bài còn nháp: không báo "đã xuất bản" sai. Cờ state chỉ có khi vừa xuất bản xong (cây có thể chưa kịp tải lại).
  if (found.lesson.status !== 'PUBLISHED' && !justPublished)
    return (
      <div className="flex flex-col gap-3">
        <p role="alert" className="font-bold text-ink-secondary">{found.lesson.title} chưa được xuất bản.</p>
        <Link to={`${courseUrl(courseId)}/bai-giang/${lessonId}`} className={`${small} self-start`}>
          Về màn chỉnh sửa
        </Link>
      </div>
    )
  return (
    <div className="flex flex-col gap-4">
      <PageHead title="Đã xuất bản bài giảng" />
      <section role="status" className="flex flex-col gap-3 rounded-[18px] border border-line-brand bg-success-bg p-5">
        <p className="break-words text-lg font-extrabold text-success-fg">{found.lesson.title}</p>
        <Link to={courseUrl(courseId)} className={`${pillPrimary} self-start`}>
          Về nội dung khóa
        </Link>
      </section>
    </div>
  )
}
