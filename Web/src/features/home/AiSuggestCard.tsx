import { Link } from 'react-router'
import aiBadge from '../../assets/icons/ai-badge.svg'
import arrow from '../../assets/icons/arrow-right-sm.svg'
import { Icon } from '../../components/Icon'
import { currentCourse } from './format'
import { useMyCourses, useQuota, useRoadmap, useToday } from './api'
import { BlockError } from './BlockState'

// Gợi ý luyện viết và lượt chấm AI của khóa đang học.
export function AiSuggestCard() {
  const courses = useMyCourses()
  const courseId = currentCourse(courses.data)?.courseId
  const roadmap = useRoadmap(courseId)
  const today = useToday(courseId)
  const quota = useQuota()
  if (courseId == null) return null

  const topic = roadmap.data?.resume?.topicTitle
  const writing = today.data?.tasks.find((t) => t.kind === 'WRITING' && t.questionId != null)
  const to = writing ? `/viet/${writing.questionId}` : '/tro-ly-cam'
  const q = quota.data?.courses.find((c) => c.courseId === courseId)
  const pool = quota.data?.pool ?? 0

  return (
    <section aria-label="Cam gợi ý" className="flex flex-col gap-3 rounded-[22px] border border-line-subtle bg-ai-bg px-6 py-[22px]">
      <span className="inline-flex items-center gap-1.5 self-start rounded-full bg-ai px-3 py-[5px] text-xs font-extrabold leading-4 text-white">
        <Icon src={aiBadge} size={12} />
        Cam gợi ý
      </span>
      <h2 className="text-lg font-black leading-6 text-ai-edge">Luyện viết một đoạn ngắn{topic ? ` về ${topic}` : ''}</h2>
      <p className="text-sm font-semibold leading-5 text-ai-edge">Trợ lý hỏi sâu từng ý rồi chấm theo rubric của Phòng Đào tạo.</p>
      {q && q.quota > 0 && (
        <div>
          <div className="mb-1.5 flex flex-wrap items-baseline justify-between gap-x-2 text-sm font-extrabold text-ai-edge">
            <span>Lượt chấm</span>
            <span>
              còn {q.remaining} lượt
              {pool > 0 && ` · +${pool} lượt mua thêm`}
            </span>
          </div>
          <div
            role="progressbar"
            aria-label="Lượt chấm đã dùng"
            aria-valuemin={0}
            aria-valuemax={q.quota}
            aria-valuenow={q.used}
            className="h-2 overflow-hidden rounded-full bg-surface"
          >
            <div className="h-full rounded-full bg-ai" style={{ width: `${Math.min(100, (q.used / q.quota) * 100)}%` }} />
          </div>
          <p className="mt-1 text-xs font-bold text-ai-edge">
            Đã dùng {q.used}/{q.quota}
          </p>
        </div>
      )}
      {quota.isError && <BlockError onRetry={() => quota.refetch()} />}
      <Link to={to} className="mt-2 inline-flex min-h-12 items-center gap-2 self-start rounded-full bg-ai px-6 text-base font-extrabold text-white shadow-edge-ai">
        Hỏi Cam
        <Icon src={arrow} size={16} />
      </Link>
    </section>
  )
}