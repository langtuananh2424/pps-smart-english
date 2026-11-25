import { Link } from 'react-router'
import { LoadMore, Loading, PageHead, RetryError } from '../ui'
import { small } from './NodeRow'
import { useCourses } from './api'
import { courseUrl, CMS, pillPrimary, StatusPill } from './ui'

// FR-ADM-01: danh sách khóa học (cả bản nháp), một thẻ, mỗi khóa một dòng (Figma 369:7990).
export function CoursesPage() {
  const q = useCourses()
  const courses = q.data?.pages.flatMap((p) => p.data) ?? []
  return (
    <div className="flex flex-col gap-4">
      <PageHead title="Khóa học" sub="Soạn và xuất bản nội dung học tập." />
      <Link to={`${CMS}/khoa-hoc/moi`} className={`${pillPrimary} self-start`}>
        Tạo khóa học
      </Link>
      {q.isPending && <Loading />}
      {q.isError && <RetryError error={q.error} onRetry={() => q.refetch()} />}
      {q.isSuccess && courses.length === 0 && <p className="font-bold text-ink-secondary">Chưa có khóa học nào.</p>}
      {courses.length > 0 && (
        <ul className="flex flex-col rounded-[18px] border border-line-brand bg-surface p-5">
          {courses.map((c) => (
            <li key={c.id} className="flex flex-wrap items-center gap-2.5 border-b border-line-brand py-3 first:pt-0 last:border-b-0 last:pb-0">
              <div className="flex min-w-0 flex-1 basis-48 flex-col gap-[3px] break-words">
                <span className="font-extrabold text-ink">{c.title}</span>
                <span className="text-sm font-semibold text-ink-secondary">{c.description || c.slug}</span>
              </div>
              <StatusPill status={c.status} />
              <Link to={courseUrl(c.id)} aria-label={`Mở nội dung ${c.title}`} className={small}>
                Mở nội dung
              </Link>
            </li>
          ))}
        </ul>
      )}
      {q.hasNextPage && <LoadMore onClick={() => q.fetchNextPage()} busy={q.isFetchingNextPage} />}
    </div>
  )
}
