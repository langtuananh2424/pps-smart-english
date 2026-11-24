import { useState } from 'react'
import { Icon } from '../../components/Icon'
import check from '../../assets/icons/check.svg'
import { usePermissionCatalog, type PermissionDef } from './api'
import { Loading, RetryError, Section } from './ui'

// Bảng chọn quyền theo module (Figma 482:15786): chip chọn module, mỗi quyền một công tắc.
export function PermissionMatrix({ selected, onToggle }: { selected: Set<string>; onToggle: (code: string) => void }) {
  const q = usePermissionCatalog()
  const [active, setActive] = useState<string>()
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />

  const groups = new Map<string, PermissionDef[]>()
  for (const p of q.data) groups.set(p.module, [...(groups.get(p.module) ?? []), p])
  const current = active && groups.has(active) ? active : [...groups.keys()][0]

  return (
    <>
      <div role="tablist" aria-label="Module" className="flex flex-wrap gap-2">
        {[...groups].map(([m, list]) => {
          const on = m === current
          return (
            <button
              key={m}
              type="button"
              role="tab"
              aria-selected={on}
              onClick={() => setActive(m)}
              className={`inline-flex min-h-11 items-center gap-1.5 rounded-md border px-3.5 font-extrabold ${on ? 'border-brand bg-subtle text-ink' : 'border-line-brand bg-surface text-ink-secondary'}`}
            >
              {on && <Icon src={check} size={16} />}
              {m} ({list.filter((p) => selected.has(p.code)).length}/{list.length})
            </button>
          )
        })}
      </div>
      <Section title="Quyền theo module">
        <ul className="flex flex-col gap-2">
          {(groups.get(current) ?? []).map((p) => (
            <li key={p.code} className="flex items-center justify-between gap-3 rounded-sm bg-input px-3 py-2.5">
              <span className="min-w-0 break-words text-sm font-extrabold text-ink">
                {p.code} · {p.description}
              </span>
              <button
                type="button"
                role="switch"
                aria-checked={selected.has(p.code)}
                aria-label={p.code}
                onClick={() => onToggle(p.code)}
                className={`relative h-6 w-11 shrink-0 rounded-full transition-colors ${selected.has(p.code) ? 'bg-brand-strong' : 'bg-muted'}`}
              >
                <span className={`absolute top-0.5 size-5 rounded-full bg-surface transition-all ${selected.has(p.code) ? 'left-[22px]' : 'left-0.5'}`} />
              </button>
            </li>
          ))}
        </ul>
      </Section>
    </>
  )
}

export const toggled = (s: Set<string>, code: string) => {
  const n = new Set(s)
  if (!n.delete(code)) n.add(code)
  return n
}
