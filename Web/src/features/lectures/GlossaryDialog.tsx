import { useEffect, useRef } from 'react'
import type { GlossaryEntry } from './api'

const canSpeak = () => typeof window !== 'undefined' && 'speechSynthesis' in window && typeof SpeechSynthesisUtterance !== 'undefined'

const Row = ({ label, children }: { label: string; children: string }) => (
  <div className="flex flex-col gap-0.5 border-t border-line-brand py-2 sm:flex-row sm:gap-4">
    <dt className="text-sm font-extrabold text-ink sm:w-24 sm:shrink-0">{label}</dt>
    <dd className="font-semibold text-ink-secondary">{children}</dd>
  </div>
)

// D-173: giải nghĩa cụm từ. Mobile là sheet dưới đáy, desktop là hộp giữa màn. Esc hoặc bấm nền để đóng.
export function GlossaryDialog({ entry, onClose }: { entry: GlossaryEntry; onClose: () => void }) {
  const box = useRef<HTMLDivElement>(null)
  const closeBtn = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    closeBtn.current?.focus()
    return () => window.speechSynthesis?.cancel()
  }, [])

  // Esc nghe ở document: focus có thể đã rời hộp thoại (bấm vào chữ trong popup).
  useEffect(() => {
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', esc)
    return () => document.removeEventListener('keydown', esc)
  }, [onClose])

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key !== 'Tab') return
    // Giữ focus trong hộp thoại.
    const items = box.current?.querySelectorAll<HTMLElement>('button')
    if (!items?.length) return
    const first = items[0]
    const last = items[items.length - 1]
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault()
      last.focus()
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault()
      first.focus()
    }
  }
  const speak = () => {
    const u = new SpeechSynthesisUtterance(entry.phrase)
    u.lang = 'en-US'
    u.rate = 0.9
    window.speechSynthesis.cancel()
    window.speechSynthesis.speak(u)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/50 md:items-center md:p-6" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={box}
        role="dialog"
        aria-modal="true"
        aria-labelledby="glossary-title"
        onKeyDown={onKeyDown}
        className="flex max-h-[85vh] w-full flex-col gap-3 overflow-y-auto rounded-t-lg bg-surface p-5 shadow-edge-card md:max-w-md md:rounded-lg"
      >
        <h2 id="glossary-title" className="break-words text-xl font-black text-ink">
          {entry.phrase}
        </h2>
        <dl className="flex flex-col">
          {entry.partOfSpeech && <Row label="Từ loại">{entry.partOfSpeech}</Row>}
          <Row label="Nghĩa">{entry.meaningVi}</Row>
          {entry.example && <Row label="Ví dụ">{entry.example}</Row>}
        </dl>
        {entry.source === 'WIKTIONARY' && <p className="text-xs font-semibold text-ink-muted">Nguồn: Wiktionary, CC BY-SA 4.0</p>}
        <div className="flex flex-wrap gap-2">
          {canSpeak() && (
            <button type="button" onClick={speak} className="inline-flex h-12 items-center rounded-full bg-brand-strong px-5 text-sm font-bold text-white">
              Nghe mẫu
            </button>
          )}
          <button ref={closeBtn} type="button" onClick={onClose} className="inline-flex h-12 items-center rounded-full border border-line-brand bg-surface px-5 text-sm font-bold text-brand-strong">
            Đóng
          </button>
        </div>
      </div>
    </div>
  )
}
