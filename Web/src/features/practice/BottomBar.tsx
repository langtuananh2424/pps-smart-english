import { pillNeutral, pillPrimary } from './questions/ui'

interface Props {
  count: number
  index: number
  answered: boolean[]
  onGo: (i: number) => void
  onSubmit: () => void
  disabled?: boolean
}

// Thanh dưới (Figma): ô số câu bên trái, "Câu trước" và "Câu tiếp"/"Nộp bài" bên phải.
export function BottomBar({ count, index, answered, onGo, onSubmit, disabled }: Props) {
  const last = index === count - 1
  return (
    <nav aria-label="Điều hướng câu hỏi" className="fixed inset-x-0 bottom-0 z-20 flex h-[72px] items-center justify-between gap-3 border-t border-line bg-surface px-4 md:px-7">
      <ol className="flex min-w-0 gap-1 overflow-x-auto">
        {Array.from({ length: count }, (_, i) => (
          <li key={i}>
            <button
              type="button"
              disabled={disabled}
              aria-label={`Câu ${i + 1}${answered[i] ? ', đã làm' : ''}`}
              aria-current={i === index ? 'step' : undefined}
              onClick={() => onGo(i)}
              className="grid size-11 shrink-0 place-items-center"
            >
              <span className={`grid size-9 place-items-center rounded-[10px] border text-[15px] font-extrabold ${
                i === index ? 'border-brand bg-page text-brand-strong' : answered[i] ? 'border-success bg-success-bg text-success-fg' : 'border-line bg-muted text-ink-secondary'
              }`}>
                {i + 1}
              </span>
            </button>
          </li>
        ))}
      </ol>
      <div className="flex shrink-0 gap-1.5 md:gap-2">
        <button type="button" disabled={disabled || index === 0} onClick={() => onGo(index - 1)} className={pillNeutral}>
          Câu trước
        </button>
        {last ? (
          <button type="button" disabled={disabled} onClick={onSubmit} className={pillPrimary}>
            Nộp bài
          </button>
        ) : (
          <button type="button" disabled={disabled} onClick={() => onGo(index + 1)} className={pillPrimary}>
            Câu tiếp
          </button>
        )}
      </div>
    </nav>
  )
}
