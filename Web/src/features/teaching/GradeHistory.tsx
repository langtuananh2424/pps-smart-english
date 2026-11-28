import { Section } from '../admin/ui'
import type { AsgProgressRow, GradeEntry, StaffMember } from './api'
import { fmtTime, score } from './format'
import { td, th } from './ui'

// Lịch sử chỉnh (Figma 480:14226): phiên bản mới nhất là "Bản hiện tại", dòng cuối là kết quả ban đầu của Cam.
export function GradeHistory({ grades, staff, row }: { grades: GradeEntry[]; staff: StaffMember[]; row?: AsgProgressRow }) {
  const sorted = [...grades].sort((a, b) => b.gradedAt.localeCompare(a.gradedAt))
  const who = (id: number) => staff.find((s) => s.userId === id)?.displayName ?? `Người dùng #${id}`
  return (
    <Section title="Lịch sử chỉnh">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[480px] border-collapse">
          <caption className="sr-only">Lịch sử chỉnh điểm</caption>
          <thead className="bg-muted">
            <tr>{['Phiên bản', 'Điểm', 'Nhận xét', 'Người cập nhật'].map((h) => <th key={h} scope="col" className={th}>{h}</th>)}</tr>
          </thead>
          <tbody>
            {sorted.map((g, i) => (
              <tr key={g.id} className="border-b border-line-brand">
                <td className={td}>{i === 0 ? 'Bản hiện tại' : `Bản ${sorted.length - i}`}</td>
                <td className={td}>{score(g.score, row?.maxScore ?? null)}</td>
                <td className={td}>{g.comment ?? '—'}</td>
                <td className={td}>{who(g.gradedBy)} · {fmtTime(g.gradedAt)}</td>
              </tr>
            ))}
            <tr>
              <td className={td}>Điểm Cam chấm · Kết quả ban đầu</td>
              <td className={td}>{score(row?.aiScore ?? null, row?.maxScore ?? null)}</td>
              <td className={td}>—</td>
              <td className={td}>Cam</td>
            </tr>
          </tbody>
        </table>
      </div>
    </Section>
  )
}
