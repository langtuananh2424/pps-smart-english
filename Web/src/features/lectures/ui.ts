// Lớp Tailwind dùng chung trong màn Bài giảng (token Figma).
export const btnNeutral =
  'inline-flex min-h-11 items-center justify-center rounded-sm border-2 border-line bg-surface px-4 py-2.5 text-sm font-semibold text-brand-strong shadow-edge-neutral'
export const btnPrimary =
  'inline-flex min-h-12 items-center justify-center rounded-md bg-brand px-5 py-3 text-lg font-black text-white shadow-edge-primary'
export const chipTone = {
  success: 'bg-success-bg text-success-fg',
  brand: 'bg-subtle text-brand-strong',
  plain: 'bg-muted text-ink-secondary',
} as const
export const chip = 'inline-flex self-start rounded-full px-2.5 py-[3px] text-xs font-extrabold leading-4'
// Nhãn trạng thái bài giảng (D-59) dùng ở danh sách bài và màn Bài tập.
export const statusBadge = {
  DONE: 'bg-success-bg text-success-fg',
  WATCHING: 'bg-subtle text-brand-strong',
  NEW: 'bg-muted text-ink-secondary',
} as const
