import { useState } from 'react'
import { Link } from 'react-router'
import { MIN_QUERY, useStudentSearch } from './studentsApi'
import { useDebounced } from './useDebounced'
import { headCls, inputCls, labelCls, Loading, PageHead, RetryError, Section } from './ui'

// FR-SUP-03: tra cứu học viên theo email, tên hoặc mã đơn (student.view). Backend giới hạn 20 kết quả.
export function StudentsPage() {
  const [text, setText] = useState('')
  const q = useDebounced(text.trim())
  const search = useStudentSearch(q)
  const short = q.length < MIN_QUERY

  return (
    <>
      <PageHead kicker="Nhân viên hỗ trợ" title="Tra cứu học viên" sub="Tìm theo email, tên hoặc mã đơn." />
      <Section title="Tra cứu">
        <div className="flex flex-col gap-2">
          <label htmlFor="s-q" className={labelCls}>
            Tiêu chí
          </label>
          <input id="s-q" value={text} onChange={(e) => setText(e.target.value)} placeholder="Nhập email, tên hoặc mã đơn" className={inputCls} />
          {short && <p className="text-sm font-semibold text-ink-secondary">Nhập ít nhất {MIN_QUERY} ký tự để tìm.</p>}
        </div>
      </Section>
      <Section title="Kết quả">
        {short ? (
          <p className="font-semibold text-ink-secondary">Kết quả sẽ hiện ở đây.</p>
        ) : search.isPending ? (
          <Loading text="Đang tìm học viên…" />
        ) : search.isError ? (
          <RetryError error={search.error} onRetry={() => search.refetch()} title="Không thể tra cứu" />
        ) : search.data.length === 0 ? (
          <p className="font-semibold text-ink-secondary">Không tìm thấy học viên nào.</p>
        ) : (
          <>
            <div className={headCls}>
              <span>Học viên</span>
              <span>Khóa</span>
              <span>Thao tác</span>
            </div>
            <ul>
              {search.data.map((s) => (
                <li key={s.id} className="grid gap-1 border-b border-line-brand p-2.5 last:border-b-0 md:grid-cols-3 md:items-center md:gap-4">
                  <span className="min-w-0 break-words font-semibold text-ink-secondary">
                    {s.displayName && <span className="block font-extrabold text-ink">{s.displayName}</span>}
                    {s.email}
                  </span>
                  <span className="font-semibold text-ink-secondary">{s.courses.length ? s.courses.join(', ') : '—'}</span>
                  <Link to={`/quan-tri/hoc-vien/${s.id}`} className="inline-flex min-h-11 items-center font-extrabold text-brand-strong underline">
                    Xem hồ sơ
                  </Link>
                </li>
              ))}
            </ul>
          </>
        )}
      </Section>
    </>
  )
}
