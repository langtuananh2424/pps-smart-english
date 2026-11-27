import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../lib/api'

// FR-AI-07, D-160: câu hỏi soạn sẵn của bộ, không có ô gõ. Lượt Thi: server trả 403 (D-151), coi như rỗng.
export interface Faq { id: number; question: string; answer: string }

export function useBankFaqs(bankId: number | undefined): Faq[] {
  const q = useQuery({
    queryKey: ['bank', bankId, 'faqs'],
    queryFn: () => api<Faq[]>(`/banks/${bankId}/faqs`).catch(() => [] as Faq[]),
    enabled: bankId != null,
    retry: false,
    staleTime: Infinity,
  })
  return q.data ?? []
}

// Accordion: mỗi lần mở một câu.
export function FaqList({ faqs }: { faqs: Faq[] }) {
  const [openId, setOpenId] = useState<number | null>(null)
  if (faqs.length === 0) return null
  return (
    <div className="mt-3 border-t border-line pt-2">
      <h3 className="text-[13px] font-extrabold text-ink-secondary">Câu hỏi thường gặp</h3>
      <ul className="flex flex-col">
        {faqs.map((f) => {
          const open = openId === f.id
          return (
            <li key={f.id}>
              <button type="button" aria-expanded={open} aria-controls={`faq-${f.id}`} onClick={() => setOpenId(open ? null : f.id)} className="flex min-h-11 w-full items-center justify-between gap-2 text-left text-sm font-extrabold text-brand-strong">
                <span>{f.question}</span>
                <span aria-hidden="true">{open ? '−' : '+'}</span>
              </button>
              {open && <p id={`faq-${f.id}`} className="pb-2 text-sm font-semibold text-ink">{f.answer}</p>}
            </li>
          )
        })}
      </ul>
    </div>
  )
}
