import { useEffect, useRef, type ReactNode } from 'react'

// Hộp thoại giữa màn (Figma 04): Esc để đóng khi chưa khóa, focus vào hộp khi mở.
export function Modal({ title, locked, onClose, children }: { title: string; locked?: boolean; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    ref.current?.focus()
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && !locked && onClose()
    window.addEventListener('keydown', esc)
    return () => window.removeEventListener('keydown', esc)
  }, [locked, onClose])
  return (
    <div className="fixed inset-0 z-30 grid place-items-center bg-ink/50 p-4">
      <div ref={ref} tabIndex={-1} role="alertdialog" aria-modal="true" aria-labelledby="modal-title" className="w-full max-w-[456px] rounded-lg bg-surface p-6 outline-none">
        <h2 id="modal-title" className="text-2xl font-black text-ink">{title}</h2>
        {children}
      </div>
    </div>
  )
}
