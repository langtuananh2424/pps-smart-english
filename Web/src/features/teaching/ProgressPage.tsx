import { useState } from 'react'
import { Link } from 'react-router'
import { PageHead, Section, inputCls, labelCls } from '../admin/ui'
import { useBoard, useClassDetail, type StudentProgress } from './api'
import { classPath, KICKER, LAG_LABEL, num } from './format'
import { Gate, NavLinkBtn, Pill, td, th, tableWrap, useNum } from './ui'

const rate = (s: StudentProgress) => (s.attendance.ratePercent == null ? '—' : `${s.attendance.ratePercent}%${s.attendance.absent ? ` · vắng ${s.attendance.absent}` : ''}`)

function Row({ s, classId }: { s: StudentProgress; classId: number }) {
  const a = s.assignments
  return (
    <tr className="border-b border-line-brand last:border-b-0">
      <td className={td}>
        <Link to={classPath(classId, `/hoc-vien/${s.userId}`)} className="font-extrabold text-brand-strong underline">
          {s.displayName}
        </Link>
        <div className="mt-1 flex flex-wrap gap-1">
          {s.lagReasons.map((r) => (
            <Pill key={r} danger>
              {LAG_LABEL[r] ?? r}
            </Pill>
          ))}
        </div>
      </td>
      <td className={td}>{s.lessonPercent}%</td>
      <td className={td}>
        {s.currentTopicTitle ?? 'Đã xong'} ({Math.min(s.unitIndex, s.totalUnits)}/{s.totalUnits})
      </td>
      <td className={td}>
        {a.submitted} nộp · {a.late} trễ · {a.overdue} quá hạn · {a.pending} chưa
      </td>
      <td className={td}>{s.avgPercent == null ? '—' : `${s.avgPercent}%`}</td>
      <td className={td}>{s.chapterTests.length ? s.chapterTests.map((t) => num(t.percent) + (t.percent == null ? '' : '%')).join(', ') : '—'}</td>
      <td className={td}>{rate(s)}</td>
    </tr>
  )
}

// #62, FR-CLS-05: bảng tiến độ lớp, lọc tụt lại và tìm theo tên ở phía client (một lớp tối đa vài chục học viên).
export function ProgressPage() {
  const classId = useNum('classId')
  const board = useBoard(classId)
  const detail = useClassDetail(classId)
  const [lagOnly, setLagOnly] = useState(false)
  const [name, setName] = useState('')
  const tab = (on: boolean) =>
    `inline-flex min-h-11 items-center rounded-md border px-3.5 font-extrabold ${on ? 'border-brand bg-subtle text-ink' : 'border-line-brand bg-surface text-ink-secondary'}`
  return (
    <>
      <PageHead kicker={KICKER} title="Tiến độ lớp" sub={detail.data?.info.name ?? 'Xem bài giảng, bài được giao, kiểm tra chương và chuyên cần.'} />
      <div className="flex flex-wrap gap-2" role="group" aria-label="Bộ lọc">
        <button type="button" aria-pressed={!lagOnly} onClick={() => setLagOnly(false)} className={tab(!lagOnly)}>
          Tất cả học viên
        </button>
        <button type="button" aria-pressed={lagOnly} onClick={() => setLagOnly(true)} className={tab(lagOnly)}>
          Chỉ học viên tụt lại
        </button>
      </div>
      <Section title="Tìm trong lớp">
        <label htmlFor="q-name" className={labelCls}>
          Tên học viên
        </label>
        <input id="q-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Nhập tên" className={inputCls} />
      </Section>
      <Gate q={board}>
        {(b) => {
          const kw = name.trim().toLowerCase()
          const rows = b.students.filter((s) => (!lagOnly || s.lagging) && s.displayName.toLowerCase().includes(kw))
          return (
            <Section title="Tiến độ học viên">
              {rows.length === 0 ? (
                <p className="font-semibold text-ink-secondary">Không có học viên phù hợp.</p>
              ) : (
                <div className={tableWrap}>
                  <table className="w-full min-w-[820px] border-collapse">
                    <caption className="sr-only">Tiến độ học viên</caption>
                    <thead className="bg-muted">
                      <tr>
                        {['Học viên', 'Bài giảng', 'Unit', 'Bài giao', 'Điểm TB', 'Kiểm tra chương', 'Chuyên cần'].map((h) => (
                          <th key={h} scope="col" className={th}>
                            {h}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((s) => (
                        <Row key={s.userId} s={s} classId={classId} />
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Section>
          )
        }}
      </Gate>
      <div className="flex flex-wrap gap-2">
        <NavLinkBtn primary to={classPath(classId, '/giao-bai')}>
          Giao bài
        </NavLinkBtn>
        <NavLinkBtn to={classPath(classId, '/mo-unit')}>Mở sớm Unit</NavLinkBtn>
        <NavLinkBtn to={classPath(classId, '/bai-giao')}>Bài đã giao của lớp</NavLinkBtn>
      </div>
    </>
  )
}
