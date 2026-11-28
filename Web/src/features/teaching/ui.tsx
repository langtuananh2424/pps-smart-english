import type { ReactNode } from 'react'
import type { UseQueryResult } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { Modal } from '../practice/Modal'
import { btnOutline, btnPrimary, Loading, RetryError } from '../admin/ui'

export { btnOutline, btnPrimary }

// Id trên đường dẫn; không phải số nguyên dương thì trả 0 (API sẽ trả 404).
export function useNum(name: string) {
  const n = Number(useParams()[name])
  return Number.isInteger(n) && n > 0 ? n : 0
}

export function Gate<T>({ q, children }: { q: UseQueryResult<T>; children: (data: T) => ReactNode }) {
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />
  return <>{children(q.data)}</>
}

export const NavLinkBtn = ({ to, children, primary }: { to: string; children: ReactNode; primary?: boolean }) => (
  <Link to={to} className={primary ? btnPrimary : btnOutline}>
    {children}
  </Link>
)

export const Pill = ({ children, danger }: { children: ReactNode; danger?: boolean }) => (
  <span className={`inline-flex rounded-full px-2.5 py-[3px] text-[13px] font-extrabold ${danger ? 'bg-danger-bg text-danger-fg' : 'bg-subtle text-brand-strong'}`}>
    {children}
  </span>
)

export function Confirm({ title, body, busy, error, onYes, onNo, yes = 'Đồng ý' }: { title: string; body: string; busy: boolean; error?: ReactNode; onYes: () => void; onNo: () => void; yes?: string }) {
  return (
    <Modal title={title} locked={busy} onClose={onNo}>
      <p className="mt-2 font-semibold text-ink-secondary">{body}</p>
      {error}
      <div className="mt-5 flex flex-wrap justify-end gap-2">
        <button type="button" onClick={onNo} disabled={busy} className={btnOutline}>
          Không
        </button>
        <button type="button" onClick={onYes} disabled={busy} className={btnPrimary}>
          {yes}
        </button>
      </div>
    </Modal>
  )
}

export const tableWrap = 'overflow-x-auto'
export const th = 'p-2.5 text-left text-[15px] font-extrabold text-ink-secondary'
export const td = 'p-2.5 align-top font-semibold text-ink'
