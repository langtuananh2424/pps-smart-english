import { useState } from 'react'
import { ApiError } from '../../lib/api'
import { PageHead, Section, WriteError, btnPrimary, labelCls } from '../admin/ui'
import { useAssignable, useClassDetail, useCreateUnlock, useRevokeUnlock, useUnlocks, type Unlock } from './api'
import { classPath, fmtTime, KICKER } from './format'
import { Recipients, toggled, type Scope } from './Recipients'
import { Confirm, Gate, NavLinkBtn, Pill, th, td, useNum } from './ui'

const MESSAGES: Record<string, string> = {
  not_sequential: 'Khóa này không bật học tuần tự',
  unlock_duplicate: 'Unit này đã được mở',
}
function UnlockError({ error }: { error: unknown }) {
  const msg = error instanceof ApiError && error.status === 409 ? MESSAGES[error.code] : undefined
  return msg ? <p role="alert" className="font-bold text-danger-fg">{msg}</p> : <WriteError error={error} />
}

// #62, FR-CLS-06, D-122: mở sớm Unit cho cả lớp hoặc một số học viên; thu hồi có hộp xác nhận.
export function UnlockPage() {
  const classId = useNum('classId')
  const tree = useAssignable(classId)
  const detail = useClassDetail(classId)
  const unlocks = useUnlocks(classId)
  const create = useCreateUnlock(classId)
  const revoke = useRevokeUnlock(classId)
  const [topicId, setTopicId] = useState(0)
  const [scope, setScope] = useState<Scope>('ALL')
  const [picked, setPicked] = useState(new Set<number>())
  const [confirming, setConfirming] = useState(false)
  const [revoking, setRevoking] = useState<Unlock | null>(null)
  const members = detail.data?.members ?? []
  const topics = tree.data?.topics ?? []
  const title = (id: number) => topics.find((t) => t.topicId === id)?.title ?? `Unit #${id}`
  const who = (u: Unlock) => (u.userId == null ? 'Cả lớp' : (members.find((m) => m.userId === u.userId)?.displayName ?? `Học viên #${u.userId}`))
  const ready = topicId > 0 && (scope === 'ALL' || picked.size > 0)

  const submit = () => create.mutate({ topicId, studentIds: scope === 'ALL' ? [] : [...picked] }, { onSuccess: () => { setConfirming(false); setPicked(new Set()) } })

  return (
    <>
      <PageHead kicker={KICKER} title="Mở sớm Unit" sub={detail.data?.info.name ?? 'Cho học viên học trước Unit chưa tới lượt.'} />
      <Section title="Chọn Unit">
        <p className="font-semibold text-ink-secondary">Chỉ áp dụng khi khóa bật học tuần tự.</p>
        <Gate q={tree}>
          {() => (
            <div className="flex flex-col gap-2">
              <label htmlFor="unit" className={labelCls}>Unit</label>
              <select id="unit" value={topicId} onChange={(e) => setTopicId(Number(e.target.value))} className="min-h-[52px] rounded-md border-[1.5px] border-line-input bg-input px-4 font-bold text-ink">
                <option value={0}>Chọn Unit</option>
                {topics.map((t) => <option key={t.topicId} value={t.topicId}>{t.title}</option>)}
              </select>
            </div>
          )}
        </Gate>
        <Recipients members={members} scope={scope} onScope={setScope} selected={picked} onToggle={(id) => setPicked((s) => toggled(s, id))} />
        <button type="button" disabled={!ready} onClick={() => setConfirming(true)} className={`${btnPrimary} self-start`}>Mở Unit</button>
      </Section>
      <Section title="Đã mở sớm">
        <Gate q={unlocks}>
          {(list) =>
            list.length === 0 ? (
              <p className="font-semibold text-ink-secondary">Chưa mở sớm Unit nào.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[640px] border-collapse">
                  <caption className="sr-only">Các lần mở sớm</caption>
                  <thead className="bg-muted"><tr>{['Unit', 'Người nhận', 'Người mở', 'Thời điểm', ''].map((h, i) => <th key={i} scope="col" className={th}>{h || <span className="sr-only">Thao tác</span>}</th>)}</tr></thead>
                  <tbody>
                    {list.map((u) => (
                      <tr key={u.id} className="border-b border-line-brand last:border-b-0">
                        <td className={td}>{title(u.topicId)}</td>
                        <td className={td}>{who(u)}</td>
                        <td className={td}>{u.grantedByName ?? '—'}</td>
                        <td className={td}>{fmtTime(u.grantedAt)}</td>
                        <td className={td}>
                          {u.revokedAt ? <Pill danger>Đã thu hồi</Pill> : (
                            <button type="button" onClick={() => setRevoking(u)} className="min-h-11 font-extrabold text-danger-fg underline">Thu hồi</button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          }
        </Gate>
      </Section>
      <div><NavLinkBtn to={classPath(classId)}>Về tiến độ lớp</NavLinkBtn></div>
      {confirming && (
        <Confirm title="Mở sớm Unit?" yes="Mở Unit" busy={create.isPending} onYes={submit} onNo={() => { setConfirming(false); create.reset() }}
          body={`${title(topicId)} sẽ mở cho ${scope === 'ALL' ? 'cả lớp' : `${picked.size} học viên`}.`}
          error={create.isError && <UnlockError error={create.error} />} />
      )}
      {revoking && (
        <Confirm title="Thu hồi lần mở sớm?" yes="Thu hồi" busy={revoke.isPending} onNo={() => { setRevoking(null); revoke.reset() }}
          onYes={() => revoke.mutate(revoking.id, { onSuccess: () => setRevoking(null) })}
          body={`Học viên sẽ không còn học trước ${title(revoking.topicId)}.`}
          error={revoke.isError && <UnlockError error={revoke.error} />} />
      )}
    </>
  )
}
