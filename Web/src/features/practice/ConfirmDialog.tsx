import { useEffect, useRef } from 'react'
import { pillNeutral, pillPrimary } from './questions/ui'

interface Props {
  unanswered: number
  busy: boolean
  error: string | null
  onConfirm: () => void
  onCancel: () => void
}

// Hộp xác nhận nộp bài tự dựng (không dùng window.confirm). Esc để đóng, focus vào hộp khi mở.
export function ConfirmDialog({ unanswered, busy, error, onConfirm, onCancel }: Props) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    ref.current?.focus()
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && !busy && onCancel()
    window.addEventListener('keydown', esc)
    return () => window.removeEventListener('keydown', esc)
  }, [busy, onCancel])
  return (
    <div className="fixed inset-0 z-30 grid place-items-center bg-ink/50 p-4">
      <div ref={ref} tabIndex={-1} role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" className="w-full max-w-sm rounded-lg bg-surface p-6 text-center outline-none">
        <h2 id="confirm-title" className="text-xl font-black text-ink">Nộp bài?</h2>
        <p className="mt-2 font-semibold text-ink-secondary">
          {unanswered > 0 ? `Bạn còn ${unanswered} câu chưa làm. Nộp rồi không sửa được.` : 'Bạn đã làm hết các câu. Nộp rồi không sửa được.'}
        </p>
        {error && <p role="alert" className="mt-2 text-sm font-bold text-danger">{error}</p>}
        <div className="mt-5 flex justify-center gap-3">
          <button type="button" disabled={busy} onClick={onCancel} className={pillNeutral}>
            Làm tiếp
          </button>
          <button type="button" disabled={busy} onClick={onConfirm} className={pillPrimary}>
            {busy ? 'Đang nộp…' : 'Nộp bài'}
          </button>
        </div>
      </div>
    </div>
  )
}
