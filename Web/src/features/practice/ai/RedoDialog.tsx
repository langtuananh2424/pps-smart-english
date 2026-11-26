import { Modal } from '../Modal'
import { pillNeutral, pillPrimary } from '../questions/ui'

// Xác nhận làm lại câu Viết/Nói (Figma 606-17885): bài mới thay bài cũ, chỉ bài mới nhất được tính.
export function RedoDialog({ title, confirm, body, onConfirm, onCancel }: { title: string; confirm: string; body: string; onConfirm: () => void; onCancel: () => void }) {
  return (
    <Modal title={title} onClose={onCancel}>
      <p className="mt-3 font-semibold text-ink-secondary">{body}</p>
      <div className="mt-5 flex justify-end gap-3">
        <button type="button" onClick={onCancel} className={pillNeutral}>Hủy</button>
        <button type="button" onClick={onConfirm} className={pillPrimary}>{confirm}</button>
      </div>
    </Modal>
  )
}
