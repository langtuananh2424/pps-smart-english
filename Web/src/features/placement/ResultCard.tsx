import { Link } from 'react-router'
import { useMyCourses } from '../home/api'
import { formatBand } from '../courses/format'
import { percent } from '../exercises/format'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { formatScore } from '../practice/ResultView'
import { SKILL_LABEL } from './labels'
import type { PlacementResult } from './types'

// D-90: band tổng, từng kỹ năng (đúng/tổng, %, band) và khóa gợi ý; không hiện đáp án từng câu.
export function ResultCard({ result, notice }: { result: PlacementResult; notice?: string }) {
  const course = result.recommendedCourse
  // Đã có khóa này ở trạng thái ACTIVE thì vào học, không bán lại; TRIAL/EXPIRED giữ nút mua.
  const owned = useMyCourses().data?.some((c) => c.courseId === course?.courseId && c.status === 'ACTIVE')
  return (
    <section aria-label="Kết quả kiểm tra đầu vào" className="mx-auto flex w-full max-w-xl flex-col gap-4">
      {notice && <p role="status" className="rounded-md bg-subtle px-4 py-3 text-sm font-bold text-brand-ink">{notice}</p>}
      <div className="flex flex-col items-center gap-1 rounded-lg border border-line-subtle bg-surface p-6 text-center shadow-edge-card">
        <p className="text-[13px] font-extrabold text-brand-strong">KẾT QUẢ KIỂM TRA ĐẦU VÀO</p>
        <p aria-label="Band tổng" className="text-6xl font-black leading-[1.1] text-ink">{formatBand(result.overallBand)}</p>
        <p className="font-bold text-ink-secondary">Band tổng</p>
      </div>
      <ul className="grid gap-3 sm:grid-cols-2">
        {Object.entries(result.skills).map(([skill, s]) => (
          <li key={skill} className="flex flex-col gap-1 rounded-lg border border-line bg-surface p-4">
            <span className="font-black text-ink">{SKILL_LABEL[skill] ?? skill}</span>
            <span className="text-3xl font-black text-brand-strong">Band {formatBand(s.band)}</span>
            <span className="text-sm font-semibold text-ink-secondary">
              Đúng {formatScore(s.correct)}/{s.total} câu · {percent(s.percent)}
            </span>
          </li>
        ))}
      </ul>
      <div className="flex flex-col gap-3 rounded-lg border border-line-brand bg-surface p-5">
        <p className="text-[13px] font-extrabold text-brand-strong">KHÓA GỢI Ý</p>
        {course ? (
          <>
            <h2 className="text-xl font-black leading-6 text-ink">{course.title}</h2>
            <p className="text-sm font-semibold text-ink-secondary">Phù hợp với band {formatBand(result.overallBand)} của bạn.</p>
            <div className="flex flex-wrap gap-3">
              {owned ? (
                <Link to={`/bai-giang/khoa/${course.courseId}`} className={pillPrimary}>Vào học</Link>
              ) : (
                <Link to={`/thanh-toan?khoa=${course.slug}`} className={pillPrimary}>Mua gói học</Link>
              )}
              <Link to="/khoa-hoc" className={pillNeutral}>Xem các khóa</Link>
            </div>
          </>
        ) : (
          <>
            <p className="font-semibold text-ink-secondary">Chưa có khóa nào khớp band {formatBand(result.overallBand)}. Hãy xem toàn bộ khóa học.</p>
            <Link to="/khoa-hoc" className={`${pillNeutral} self-start`}>Xem các khóa</Link>
          </>
        )}
      </div>
    </section>
  )
}
