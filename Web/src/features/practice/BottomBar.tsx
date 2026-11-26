import type { SendAction } from './ai/answer'
import { pillNeutral, pillPrimary } from './questions/ui'

interface Props {
  count: number
  index: number
  answered: boolean[]
  onGo: (i: number) => void
  onSubmit: () => void
  disabled?: boolean
  submitDisabled?: boolean // đang tải bản ghi lên: chưa cho nộp
  send?: SendAction | null // "Gửi chấm" của câu Viết/Nói chưa gửi
}

// Thanh dưới (Figma): ô số câu; "Câu trước", "Câu tiếp"/"Nộp bài" và "Gửi chấm". Mobile xếp hai hàng.
export function BottomBar({ count, index, answered, onGo, onSubmit, disabled, submitDisabled, send }: Props) {
  const last = index === count - 1
  const next = send ? pillNeutral : pillPrimary // có "Gửi chấm" thì nó là nút chính
  return (
    <nav aria-label="Điều hướng câu hỏi" className="fixed inset-x-0 bottom-0 z-20 flex flex-col gap-1 border-t border-line bg-surface px-4 py-2 md:h-[72px] md:flex-row md:items-center md:justify-between md:gap-3 md:px-7 md:py-0">
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
                answered[i] ? 'border-success bg-success-bg text-success-fg' : i === index ? 'border-brand bg-page text-brand-strong' : 'border-line bg-muted text-ink-secondary'
              }`}>
                {i + 1}
              </span>
            </button>
          </li>
        ))}
      </ol>
      <div className="flex shrink-0 justify-end gap-1.5 md:gap-2">
        <button type="button" disabled={disabled || index === 0} onClick={() => onGo(index - 1)} className={pillNeutral}>
          Câu trước
        </button>
        {last ? (
          <button type="button" disabled={disabled || submitDisabled} onClick={onSubmit} className={next}>
            Nộp bài
          </button>
        ) : (
          <button type="button" disabled={disabled} onClick={() => onGo(index + 1)} className={next}>
            Câu tiếp
          </button>
        )}
        {send && (
          <button type="button" disabled={disabled || !send.enabled} onClick={send.run} className={pillPrimary}>
            Gửi chấm
          </button>
        )}
      </div>
    </nav>
  )
}
