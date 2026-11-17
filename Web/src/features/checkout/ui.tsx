import type { ReactNode } from 'react'
import { STATUS_BADGE } from './format'
import type { OrderStatus } from './api'

export const btnPrimary =
  'inline-flex h-12 items-center justify-center rounded-sm bg-brand-strong px-4 text-[15px] font-extrabold text-white disabled:opacity-60'
export const btnOutline =
  'inline-flex h-12 items-center justify-center rounded-sm border border-line bg-surface px-4 text-[15px] font-extrabold text-brand-strong disabled:opacity-60'
export const card = 'flex flex-col gap-3 rounded-[18px] border border-line bg-surface p-5 lg:rounded-lg'

export function Pill({ children, className }: { children: ReactNode; className: string }) {
  return <span className={`inline-flex self-start rounded-full px-2.5 py-[5px] text-[13px] font-extrabold leading-[18px] ${className}`}>{children}</span>
}

export const StatusBadge = ({ status }: { status: OrderStatus }) => <Pill className={STATUS_BADGE[status].cls}>{STATUS_BADGE[status].label}</Pill>

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1 break-words">
      <dt className="text-[13px] font-extrabold leading-[18px] text-ink-secondary">{label}</dt>
      <dd className="text-base font-extrabold leading-[22px] text-ink">{children}</dd>
    </div>
  )
}

export function PageHead({ title, sub }: { title: string; sub?: string }) {
  return (
    <header className="flex flex-col gap-1">
      <h1 className="text-2xl font-black leading-[30px] text-ink lg:text-4xl lg:leading-[50px]">{title}</h1>
      {sub && <p className="text-[15px] font-semibold text-ink-secondary">{sub}</p>}
    </header>
  )
}

export function Notice({ title, body, children }: { title: string; body: string; children: ReactNode }) {
  return (
    <section role="alert" className={card}>
      <h2 className="text-xl font-black text-ink">{title}</h2>
      <p className="font-semibold text-ink-secondary">{body}</p>
      <div className="flex flex-wrap gap-2">{children}</div>
    </section>
  )
}
