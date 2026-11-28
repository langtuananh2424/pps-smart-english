import { Link } from 'react-router'
import { PageHead, Section } from '../admin/ui'
import { useClassAssignments, useClassDetail, useTeachingClasses, type TeachAssignment } from './api'
import { asgState, asgTitle, classPath, fmtTime, KICKER, num } from './format'
import { Gate, NavLinkBtn, Pill, td, th, useNum } from './ui'

function Table({ classId, list }: { classId: number; list: TeachAssignment[] }) {
  if (list.length === 0) return <p className="font-semibold text-ink-secondary">Chưa giao bài nào.</p>
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] border-collapse">
        <caption className="sr-only">Bài đã giao</caption>
        <thead className="bg-muted">
          <tr>{['Bài', 'Hạn nộp', 'Đã nộp', 'Trạng thái', ''].map((h, i) => <th key={i} scope="col" className={th}>{h || <span className="sr-only">Chi tiết</span>}</th>)}</tr>
        </thead>
        <tbody>
          {list.map((a) => {
            const st = asgState(a)
            return (
              <tr key={a.id} className="border-b border-line-brand last:border-b-0">
                <td className={td}>{asgTitle(a)}</td>
                <td className={td}>{fmtTime(a.dueAt)}</td>
                <td className={td}>{a.submitted == null ? '—' : `${a.submitted}/${num(a.recipients)}`}</td>
                <td className={td}><Pill danger={st.danger}>{st.label}</Pill></td>
                <td className={td}>
                  <Link to={classPath(classId, `/bai-giao/${a.id}`)} aria-label={`Xem chi tiết ${asgTitle(a)}`} className="min-h-11 font-extrabold text-brand-strong underline">Xem chi tiết</Link>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function ClassBlock({ id, name }: { id: number; name: string }) {
  const q = useClassAssignments(id)
  return (
    <Section title={name}>
      <Gate q={q}>{(list) => <Table classId={id} list={list} />}</Gate>
    </Section>
  )
}

// #61, FR-ASG-04: bài đã giao của một lớp.
export function ClassAssignmentsPage() {
  const classId = useNum('classId')
  const detail = useClassDetail(classId)
  return (
    <>
      <PageHead kicker={KICKER} title="Bài đã giao" sub={detail.data?.info.name} />
      <ClassBlock id={classId} name="Danh sách bài" />
      <div className="flex flex-wrap gap-2">
        <NavLinkBtn primary to={classPath(classId, '/giao-bai')}>Giao bài</NavLinkBtn>
        <NavLinkBtn to={classPath(classId)}>Về tiến độ lớp</NavLinkBtn>
      </div>
    </>
  )
}

// Mọi lớp của tôi, nhóm theo lớp.
// shortcut: mỗi lớp một truy vấn (số lớp của một giáo viên nhỏ); gộp thành API riêng khi có giáo viên dạy hàng chục lớp.
export function AllAssignmentsPage() {
  const q = useTeachingClasses()
  return (
    <>
      <PageHead kicker={KICKER} title="Bài đã giao" sub="Bài đã giao ở các lớp bạn phụ trách." />
      <Gate q={q}>{(list) => (list.length === 0 ? <p className="font-semibold text-ink-secondary">Bạn chưa được phân công lớp nào.</p> : list.map((c) => <ClassBlock key={c.id} id={c.id} name={c.name} />))}</Gate>
    </>
  )
}
