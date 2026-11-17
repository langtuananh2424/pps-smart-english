// Lớp Tailwind dùng chung cho màn làm bài (token Figma 10.01, 10.36, 415:*).

// Dòng lựa chọn (MCQ, chọn từ sai): cao 52, viền 1.5, mã chữ trong ô 32.
export const option = (active: boolean) =>
  `flex min-h-[52px] w-full items-center gap-2.5 rounded-md border-[1.5px] py-2 pl-2 pr-5 text-left text-base font-semibold text-ink transition-colors disabled:opacity-60 ${
    active ? 'border-brand bg-subtle' : 'border-line bg-surface hover:border-line-brand'
  }`
export const optionCode = 'grid size-8 shrink-0 place-items-center rounded-sm bg-subtle text-[13px] font-extrabold text-ink-secondary'

// Ô nhập: cao 52, nền input, viền input.
export const field =
  'min-h-[52px] w-full rounded-md border-[1.5px] border-line-input bg-input px-4 py-3 text-base font-semibold text-ink outline-none placeholder:text-ink-muted focus:border-brand disabled:opacity-60'
export const fieldLabel = 'text-[13px] font-extrabold leading-[18px] text-ink-secondary'

// Chip từ trong câu (nhận diện cấu trúc / chọn từ): nền muted, chọn thì nền brand-soft.
export const token = (active: boolean) =>
  `min-h-11 rounded-[8px] px-3 text-[15px] font-bold text-ink disabled:opacity-60 ${active ? 'bg-brand-soft' : 'bg-muted'}`

// Thẻ con trong màn nhận diện / xếp khối.
export const panel = 'flex flex-col gap-3 rounded-[22px] border border-line-brand bg-surface p-4'
export const panelTitle = 'text-lg font-black text-ink'

// Nút pill của thanh dưới và màn kết quả (Figma Button: Primary gradient cam, Secondary viền trắng).
const pill = 'inline-flex items-center justify-center gap-2 rounded-full text-lg font-black disabled:opacity-45'
export const pillNeutral = `${pill} h-12 border-[1.5px] border-line-input bg-surface px-4 text-ink md:h-14 md:px-6`
export const pillPrimary = `${pill} h-12 bg-gradient-to-r from-[#e8590c] via-[#f76707] to-[#ff8a3d] px-4 text-white shadow-[0_8px_11px_rgba(232,89,12,0.3)] md:h-14 md:px-6`
