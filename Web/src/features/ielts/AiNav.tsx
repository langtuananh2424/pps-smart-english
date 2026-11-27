interface Props {
  tabs?: { id: number; label: string }[]
  currentId: number
  onGo: (id: number) => void
  onSubmit: () => void
  submitLabel: string
  disabled: boolean
}

// Thanh đáy khung Viết/Nói (Figma): tab Task 1/Task 2 (chỉ Viết) và nút nộp lượt.
export function AiNav({ tabs, currentId, onGo, onSubmit, submitLabel, disabled }: Props) {
  return (
    <nav aria-label="Điều hướng bài" className="fixed inset-x-0 bottom-0 z-10 flex items-center justify-between gap-3 border-t border-line bg-surface px-3 py-3 md:px-7 land:static land:shrink-0 land:py-1.5">
      <div role="tablist" aria-label="Task" className="flex min-w-0 flex-1 gap-2 overflow-x-auto">
        {tabs?.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={t.id === currentId}
            disabled={disabled}
            onClick={() => onGo(t.id)}
            className={`min-h-11 land:min-h-9 shrink-0 rounded-full border px-4 text-sm font-extrabold ${t.id === currentId ? 'border-2 border-brand bg-brand-soft text-ink' : 'border-line bg-surface text-ink-secondary'}`}
          >
            {t.label}
          </button>
        ))}
      </div>
      <button type="button" disabled={disabled} onClick={onSubmit} className="min-h-11 land:min-h-9 shrink-0 rounded-full bg-gradient-to-r from-[#e8590c] to-[#ff8a3d] px-5 text-base font-black text-white disabled:opacity-45">
        {submitLabel}
      </button>
    </nav>
  )
}
