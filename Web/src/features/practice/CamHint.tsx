import { useEffect, useState } from 'react'
import cam from '../../assets/cam-chao.png'
import { FaqList, useBankFaqs } from './CamFaqs'
import type { Hint } from './types'

// Cam nổi góc dưới (Figma 10.01): chỉ có khi câu có gợi ý soạn sẵn (bộ EXAM backend trả rỗng).
// Mobile có thêm nhãn "Hỏi Cam" cạnh Cam; bấm thì mở bong bóng liệt kê gợi ý.
// D-150: kiểm tra sai thì Player đổi openKey để Cam tự mở, notice ("Cần sửa...") hiện trên danh sách gợi ý; câu không có gợi ý vẫn có Cam khi có notice.
// D-160: faqBankId có thì bên dưới gợi ý là danh sách câu hỏi soạn sẵn của bộ (không truyền ở lượt Thi).
export function CamHint({ hints, notice, openKey, faqBankId }: { hints: Hint[]; notice?: string | null; openKey?: number; faqBankId?: number }) {
  const [open, setOpen] = useState(false)
  const faqs = useBankFaqs(faqBankId)
  useEffect(() => {
    if (openKey) setOpen(true)
  }, [openKey])
  if (hints.length === 0 && !notice && faqs.length === 0) return null
  return (
    <div className="fixed bottom-[88px] right-4 z-10 flex max-w-[calc(100vw-2rem)] flex-col items-end gap-2 md:right-7">
      {open && (
        <section aria-label="Gợi ý của Cam" className="max-h-[60dvh] w-[282px] overflow-y-auto rounded-[20px] border border-line bg-surface p-3.5 shadow-edge-card md:w-[326px]">
          <div className="flex h-11 items-center justify-between">
            <h2 className="text-[15px] font-extrabold text-brand-strong">Gợi ý của Cam</h2>
            <button type="button" aria-label="Đóng gợi ý" onClick={() => setOpen(false)} className="grid size-11 place-items-center rounded-[10px] bg-input text-[13px] font-extrabold text-ink">
              ×
            </button>
          </div>
          {notice && <p role="status" className="mb-2 text-sm font-extrabold text-danger-fg">{notice}</p>}
          <ul className="flex flex-col gap-2 text-sm font-semibold text-ink">
            {hints.map((h) => (
              <li key={h.id}>
                {h.content}
                {h.detail && <span className="mt-0.5 block text-ink-secondary">{h.detail}</span>}
              </li>
            ))}
          </ul>
          <FaqList faqs={faqs} />
        </section>
      )}
      <div className="flex items-center gap-2">
        {!open && <span aria-hidden="true" className="flex h-11 items-center rounded-full border border-line bg-surface px-3 text-[15px] font-extrabold text-brand-strong md:hidden">Hỏi Cam</span>}
        <button type="button" aria-expanded={open} aria-label="Hỏi Cam" onClick={() => setOpen((v) => !v)} className="grid size-[60px] shrink-0 place-items-center rounded-full border border-line bg-surface p-0.5 md:size-[76px] md:p-[2.5px]">
          <img src={cam} alt="" className="size-full object-contain" />
        </button>
      </div>
    </div>
  )
}
