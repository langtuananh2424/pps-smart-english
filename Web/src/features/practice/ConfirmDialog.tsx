import { Modal } from './Modal'
import { pillNeutral, pillPrimary } from './questions/ui'

interface Props {
  pending: number[] // chỉ số các câu chưa làm
  zeroAi: number // trong số đó, câu Viết/Nói sẽ tính 0 điểm
  grading: number // câu Viết/Nói đang chấm
  busy: boolean
  error: string | null
  onGo: (index: number) => void
  onConfirm: () => void
  onCancel: () => void
}

// Hộp xác nhận nộp bài (Figma 04): liệt kê câu chưa làm (bấm để quay lại), cảnh báo 0 điểm và câu đang chấm chỉ khi có.
export function ConfirmDialog({ pending, zeroAi, grading, busy, error, onGo, onConfirm, onCancel }: Props) {
  return (
    <Modal title="Nộp bài?" locked={busy} onClose={onCancel}>
      {pending.length > 0 ? (
        <>
          <p className="mt-3 font-extrabold text-ink">Còn {pending.length} câu chưa làm</p>
          <ul className="mt-3 flex flex-wrap gap-2">
            {pending.map((i) => (
              <li key={i}>
                <button type="button" disabled={busy} aria-label={`Quay lại câu ${i + 1}`} onClick={() => onGo(i)} className="grid size-9 place-items-center rounded-[10px] border border-line bg-muted text-[15px] font-extrabold text-ink-secondary">
                  {i + 1}
                </button>
              </li>
            ))}
          </ul>
          {zeroAi > 0 && <p className="mt-3 text-sm font-semibold text-ink-secondary">Trong đó {zeroAi} câu Viết/Nói sẽ tính 0 điểm.</p>}
        </>
      ) : (
        <p className="mt-3 font-semibold text-ink-secondary">Bạn đã làm hết các câu.</p>
      )}
      {grading > 0 && (
        <p role="status" className="mt-4 rounded-md bg-reward-bg p-3 text-sm font-bold text-reward-fg">
          {grading} câu Viết/Nói đang chấm. Kết quả sẽ hiện khi chấm xong.
        </p>
      )}
      {error && <p role="alert" className="mt-3 text-sm font-bold text-danger">{error}</p>}
      <div className="mt-5 flex justify-end gap-3">
        <button type="button" disabled={busy} onClick={onCancel} className={pillNeutral}>
          Làm tiếp
        </button>
        <button type="button" disabled={busy} onClick={onConfirm} className={pillPrimary}>
          {busy ? 'Đang nộp…' : 'Nộp bài'}
        </button>
      </div>
    </Modal>
  )
}
