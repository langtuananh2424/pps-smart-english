import { useState } from 'react'
import { ApiError } from '../../lib/api'
import { fmtTime } from '../teaching/format'
import { validateRange } from './ExportCsvDialog'
import { btnPrimary, inputCls, labelCls, Loading, PageHead, RetryError, Section } from './ui'
import { exportTeachingCsv, useRefreshReport, useReportClasses, useTeachingReport, type ReportFilters, type TeacherRow } from './teachingReportApi'

const ROLE_LABEL = { TEACHER: 'Giáo viên', ASSISTANT: 'Trợ giảng' } as const
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const monthRange = () => {
  const now = new Date()
  return { from: iso(new Date(now.getFullYear(), now.getMonth(), 1)), to: iso(now) }
}
const nf = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 })
const hours = (h: number) => `${nf.format(h)} giờ`
const cell = 'block p-2.5 font-semibold text-ink md:table-cell'
const th = 'p-2.5 text-left text-[15px] font-extrabold text-ink-secondary'
const rowCls = 'block rounded-md border border-line-brand p-2 md:table-row md:rounded-none md:border-0 md:border-b md:p-0'

function Row({ t }: { t: TeacherRow }) {
  return (
    <tr className={rowCls}>
      <td className={`${cell} font-black`}>{t.displayName}</td>
      <td className={cell}>{ROLE_LABEL[t.role] ?? t.role}</td>
      <td className={cell}>
        <span className="md:hidden">Buổi nhóm: </span>
        {t.groupSessions}
      </td>
      <td className={cell}>
        <span className="md:hidden">Buổi kèm 1-1: </span>
        {t.tutoringSessions}
      </td>
      <td className={cell}>
        <span className="md:hidden">Tổng giờ: </span>
        {hours(t.totalHours)}
      </td>
    </tr>
  )
}

// FR-CLS-05, D-176: báo cáo buổi dạy đã điểm danh, không tính lương.
export function TeachingReportPage() {
  const [f, setF] = useState<ReportFilters>({ ...monthRange(), role: '', classId: '' })
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const range = validateRange(f.from, f.to)
  const valid = !range.from && !range.to
  const q = useTeachingReport(f, valid)
  const classes = useReportClasses()
  const refresh = useRefreshReport()
  const set = (k: keyof ReportFilters) => (e: { target: { value: string } }) => setF((s) => ({ ...s, [k]: e.target.value }))

  async function exportFile() {
    setBusy(true)
    setFailure(null)
    try {
      const { blob, filename } = await exportTeachingCsv(f)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
      await refresh()
    } catch (err) {
      setFailure(err instanceof ApiError ? (err.status === 403 ? 'Bạn không có quyền xuất báo cáo.' : err.message) : 'Không thể xuất file. Kiểm tra kết nối rồi thử lại.')
    } finally {
      setBusy(false)
    }
  }

  const err = (m?: string, id?: string) => m && <p id={id} role="alert" className="text-sm font-bold text-danger-fg">{m}</p>
  const r = q.data

  return (
    <>
      <PageHead title="Báo cáo số buổi dạy" sub="Chỉ tính các buổi đã điểm danh. Hệ thống không tính lương." />
      <section aria-label="Bộ lọc" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="flex min-w-0 flex-col gap-2">
          <label htmlFor="rp-from" className={labelCls}>Từ ngày</label>
          <input id="rp-from" type="date" value={f.from} onChange={set('from')} aria-invalid={!!range.from} className={inputCls} />
          {err(range.from)}
        </div>
        <div className="flex min-w-0 flex-col gap-2">
          <label htmlFor="rp-to" className={labelCls}>Đến ngày</label>
          <input id="rp-to" type="date" value={f.to} onChange={set('to')} aria-invalid={!!range.to} className={inputCls} />
          {err(range.to)}
        </div>
        <div className="flex min-w-0 flex-col gap-2">
          <label htmlFor="rp-role" className={labelCls}>Vai trò</label>
          <select id="rp-role" value={f.role} onChange={set('role')} className={inputCls}>
            <option value="">Tất cả</option>
            <option value="TEACHER">Giáo viên</option>
            <option value="ASSISTANT">Trợ giảng</option>
          </select>
        </div>
        <div className="flex min-w-0 flex-col gap-2">
          <label htmlFor="rp-class" className={labelCls}>Lớp</label>
          <select id="rp-class" value={f.classId} onChange={set('classId')} className={inputCls}>
            <option value="">Tất cả lớp</option>
            {classes.data?.map((c) => (
              <option key={c.id} value={c.id}>{c.name} · {c.courseTitle}</option>
            ))}
          </select>
        </div>
      </section>

      {!valid ? null : q.isPending ? (
        <Loading />
      ) : q.isError ? (
        <RetryError error={q.error} onRetry={() => q.refetch()} />
      ) : (
        <>
          <Section title="Tổng hợp theo người dạy">
            {r!.teachers.length === 0 ? (
              <p className="font-semibold text-ink-secondary">Không có buổi dạy nào đã điểm danh trong khoảng này.</p>
            ) : (
              <table className="block w-full border-collapse md:table">
                <caption className="sr-only">Tổng hợp theo người dạy</caption>
                <thead className="hidden bg-muted md:table-header-group">
                  <tr>
                    {['Người dạy', 'Vai trò', 'Buổi nhóm', 'Buổi kèm 1-1', 'Tổng giờ'].map((h) => (
                      <th key={h} scope="col" className={th}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="flex flex-col gap-3 md:table-row-group">
                  {r!.teachers.map((t) => <Row key={`${t.userId}-${t.role}`} t={t} />)}
                </tbody>
              </table>
            )}
          </Section>

          {r!.editedSessions.length > 0 && (
            <section aria-label="Buổi bị sửa điểm danh" className="flex flex-col gap-2.5 rounded-lg bg-reward-bg p-5">
              <h2 className="text-lg font-black text-ink">Buổi bị sửa điểm danh sau lần xuất trước</h2>
              <p className="font-semibold text-ink-secondary">Lần xuất trước: {r!.lastExportAt ? fmtTime(r!.lastExportAt) : 'Chưa từng xuất'}</p>
              <ul className="flex flex-col gap-2">
                {r!.editedSessions.map((s) => (
                  <li key={s.sessionId} className="rounded-md bg-surface p-3 font-semibold text-ink">
                    <span className="font-black">{s.className}</span> · {s.teacherName} · {fmtTime(s.startsAt)}
                    <span className="block text-sm text-ink-secondary">Sửa {s.edits} lần, lần cuối {fmtTime(s.lastEditedAt)}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          <div className="flex flex-col items-start gap-2">
            <button type="button" onClick={exportFile} disabled={busy} className={btnPrimary}>
              {busy ? 'Đang xuất…' : 'Xuất file'}
            </button>
            {failure && <p role="alert" className="font-bold text-danger-fg">{failure}</p>}
          </div>
        </>
      )}
    </>
  )
}
