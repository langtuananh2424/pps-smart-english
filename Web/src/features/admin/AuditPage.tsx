import { useState } from 'react'
import { useAuditLogs, useMyPermissions, useStaffOptions, type AuditEntry } from './api'
import { useDebounced } from './useDebounced'
import { headCls, inputCls, labelCls, Loading, LoadMore, PageHead, RetryError, Section } from './ui'

const rowGrid = 'grid gap-1 md:grid-cols-5 md:items-center md:gap-4'
const who = (e: AuditEntry) => e.actorName || e.actorEmail || `#${e.actorId}`
const json = (v: unknown) => (v == null ? 'Không có' : JSON.stringify(v, null, 2))

function Field({ id, label, children }: { id: string; label: string; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <label htmlFor={id} className={labelCls}>
        {label}
      </label>
      {children}
    </div>
  )
}

// FR-ADM-05: nhật ký thao tác (Figma 482:16311). Chọn một dòng để xem giá trị Trước/Sau.
export function AuditPage() {
  const perms = useMyPermissions()
  // Danh sách nhân sự chỉ đọc được với rbac.manage; không có quyền đó thì nhập mã người thực hiện.
  const canPickActor = !!perms.data?.has('rbac.manage')
  const staff = useStaffOptions(canPickActor)
  const [f, setF] = useState({ actorId: '', targetType: '', targetId: '', from: '', to: '' })
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((s) => ({ ...s, [k]: e.target.value }))
  const debounced = useDebounced(f)
  const list = useAuditLogs({ ...debounced, targetType: debounced.targetType.trim(), targetId: debounced.targetId.trim(), actorId: debounced.actorId.trim() })
  const rows = list.data?.pages.flatMap((p) => p.data) ?? []
  const [selectedId, setSelectedId] = useState<number>()
  const selected = rows.find((r) => r.id === selectedId)

  return (
    <>
      <PageHead title="Nhật ký thao tác" sub="Tra cứu thay đổi dữ liệu của nhân sự." />
      <Section title="Bộ lọc">
        <div className="grid gap-3 md:grid-cols-3">
          <Field id="f-actor" label="Người thực hiện">
            {canPickActor ? (
              <select id="f-actor" value={f.actorId} onChange={set('actorId')} className={inputCls}>
                <option value="">Tất cả nhân sự</option>
                {staff.data?.pages.flatMap((p) => p.data).map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.displayName} ({u.email})
                  </option>
                ))}
              </select>
            ) : (
              <input id="f-actor" inputMode="numeric" value={f.actorId} onChange={(e) => setF((s) => ({ ...s, actorId: e.target.value.replace(/\D/g, '') }))} placeholder="Mã nhân sự" className={inputCls} />
            )}
          </Field>
          <Field id="f-type" label="Loại đối tượng">
            <input id="f-type" value={f.targetType} onChange={set('targetType')} placeholder="VD: ROLE, USER" className={inputCls} />
          </Field>
          <Field id="f-target" label="Mã đối tượng">
            <input id="f-target" value={f.targetId} onChange={set('targetId')} placeholder="Tất cả đối tượng" className={inputCls} />
          </Field>
          <Field id="f-from" label="Từ ngày">
            <input id="f-from" type="date" value={f.from} onChange={set('from')} className={inputCls} />
          </Field>
          <Field id="f-to" label="Đến ngày">
            <input id="f-to" type="date" value={f.to} onChange={set('to')} className={inputCls} />
          </Field>
        </div>
      </Section>

      <Section title="Bản ghi">
        {list.isPending ? (
          <Loading />
        ) : list.isError ? (
          <RetryError error={list.error} onRetry={() => list.refetch()} />
        ) : rows.length === 0 ? (
          <p className="font-semibold text-ink-secondary">Chưa có bản ghi nào.</p>
        ) : (
          <>
            <div className={`${headCls} md:grid-cols-5`}>
              <span>Thao tác</span>
              <span>Thời điểm</span>
              <span>Người thực hiện</span>
              <span>Đối tượng</span>
              <span>Lý do</span>
            </div>
            <ul>
              {rows.map((r) => (
                <li key={r.id} className="border-b border-line-brand last:border-b-0">
                  <button
                    type="button"
                    aria-pressed={r.id === selectedId}
                    onClick={() => setSelectedId(r.id)}
                    className={`${rowGrid} w-full rounded-sm p-2.5 text-left font-semibold text-ink-secondary hover:bg-subtle ${r.id === selectedId ? 'bg-subtle' : ''}`}
                  >
                    <span className="font-extrabold text-ink">{r.action}</span>
                    <span>{new Date(r.createdAt).toLocaleString('vi-VN')}</span>
                    <span className="break-words">{who(r)}</span>
                    <span className="break-words">
                      {r.targetType} #{r.targetId}
                    </span>
                    <span className="break-words">{r.reason ?? '—'}</span>
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}
      </Section>
      {list.hasNextPage && <LoadMore onClick={() => list.fetchNextPage()} busy={list.isFetchingNextPage} />}

      <div className="grid gap-3 md:grid-cols-2">
        <section aria-label="Trước" className="flex min-w-0 flex-col gap-2 rounded-lg border border-line-brand bg-muted p-5">
          <h2 className="text-lg font-black text-ink-secondary">Trước</h2>
          <pre className="overflow-x-auto whitespace-pre-wrap break-words text-sm font-semibold text-ink-secondary">{selected ? json(selected.before) : 'Chọn một bản ghi để xem giá trị tại thời điểm trước thao tác.'}</pre>
        </section>
        <section aria-label="Sau" className="flex min-w-0 flex-col gap-2 rounded-lg border border-line-brand bg-success-bg p-5">
          <h2 className="text-lg font-black text-success-fg">Sau</h2>
          <pre className="overflow-x-auto whitespace-pre-wrap break-words text-sm font-semibold text-ink-secondary">{selected ? json(selected.after) : 'Chọn một bản ghi để xem giá trị sau khi lưu.'}</pre>
        </section>
      </div>
    </>
  )
}
