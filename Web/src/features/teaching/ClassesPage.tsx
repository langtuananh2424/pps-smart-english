import { Link } from 'react-router'
import { PageHead, Section } from '../admin/ui'
import { useTeachingClasses } from './api'
import { classPath, CLASS_STATUS, KICKER, MODE_LABEL } from './format'
import { Gate, Pill, td, th } from './ui'

// Mobile: mỗi dòng thành một thẻ (thead ẩn, ô xếp dọc); từ md trở lên là bảng.
const cell = `${td} block md:table-cell`

// #62, FR-CLS-05: lớp tôi được phân công (GET /teaching/classes). Figma 480:11653.
export function ClassesPage() {
  const q = useTeachingClasses()
  return (
    <>
      <PageHead kicker={KICKER} title="Lớp của tôi" sub="Các lớp bạn đang được phân công." />
      <Gate q={q}>
        {(list) =>
          list.length === 0 ? (
            <p className="font-semibold text-ink-secondary">Bạn chưa được phân công lớp nào.</p>
          ) : (
            <Section title="Danh sách lớp">
              <table className="block w-full border-collapse md:table">
                <caption className="sr-only">Lớp của tôi</caption>
                <thead className="hidden bg-muted md:table-header-group">
                  <tr>
                    {['Lớp/khóa', 'Hình thức', 'Sĩ số', 'Tiến độ TB', 'Theo dõi', ''].map((h, i) => <th key={i} scope="col" className={th}>{h || <span className="sr-only">Tiến độ</span>}</th>)}
                  </tr>
                </thead>
                <tbody className="flex flex-col gap-3 md:table-row-group">
                  {list.map((c) => (
                    <tr key={c.id} className="block rounded-md border border-line-brand p-2 md:table-row md:rounded-none md:border-0 md:border-b md:p-0">
                      <td className={cell}>
                        <span className="block font-black">{c.name}</span>
                        <span className="block text-ink-secondary">{c.courseTitle}</span>
                        <Pill danger={c.status === 'CLOSED'}>{CLASS_STATUS[c.status] ?? c.status}</Pill>
                      </td>
                      <td className={cell}>{MODE_LABEL[c.mode]}</td>
                      <td className={cell}>{c.activeMembers}/{c.capacity}</td>
                      <td className={cell}>{c.avgLessonPercent == null ? '—' : `${c.avgLessonPercent}%`}</td>
                      <td className={cell}>
                        {c.laggingCount > 0 ? (
                          <Link to={`${classPath(c.id)}?loc=tut-lai`} className="font-extrabold text-danger-fg underline">
                            {c.laggingCount} học viên cần hỗ trợ
                          </Link>
                        ) : (
                          'Ổn định'
                        )}
                      </td>
                      <td className={cell}>
                        <Link to={classPath(c.id)} aria-label={`Xem tiến độ lớp ${c.name}`} className="inline-flex min-h-11 items-center font-extrabold text-brand-strong underline">
                          Xem tiến độ
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Section>
          )
        }
      </Gate>
    </>
  )
}
