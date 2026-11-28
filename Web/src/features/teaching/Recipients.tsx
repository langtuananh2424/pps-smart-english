import type { Member } from './api'

export type Scope = 'ALL' | 'SOME'

// Chọn người nhận: cả lớp hoặc một số học viên đang học (dùng cho mở sớm Unit và giao bài).
export function Recipients({ members, scope, onScope, selected, onToggle }: {
  members: Member[]
  scope: Scope
  onScope: (s: Scope) => void
  selected: Set<number>
  onToggle: (id: number) => void
}) {
  const active = members.filter((m) => m.status === 'ACTIVE')
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-extrabold text-ink">Người nhận</legend>
      {([['ALL', 'Cả lớp'], ['SOME', 'Chọn học viên']] as const).map(([v, label]) => (
        <label key={v} className="flex min-h-11 items-center gap-2 font-bold text-ink">
          <input type="radio" name="scope" checked={scope === v} onChange={() => onScope(v)} className="size-5 accent-brand-strong" />
          {label}
        </label>
      ))}
      {scope === 'SOME' && (
        <ul className="grid gap-1 md:grid-cols-2">
          {active.map((m) => (
            <li key={m.userId}>
              <label className="flex min-h-11 items-center gap-2 font-semibold text-ink">
                <input type="checkbox" checked={selected.has(m.userId)} onChange={() => onToggle(m.userId)} className="size-5 accent-brand-strong" />
                {m.displayName}
              </label>
            </li>
          ))}
        </ul>
      )}
    </fieldset>
  )
}


export const toggled = (s: Set<number>, id: number) => {
  const n = new Set(s)
  if (!n.delete(id)) n.add(id)
  return n
}
