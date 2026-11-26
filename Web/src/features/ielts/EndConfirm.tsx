import { Modal } from '../practice/Modal'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'

interface Props { busy: boolean; error: string | null; onBack: () => void; onConfirm: () => void }

// Figma 00.04 · Xác nhận kết thúc: Làm tiếp hoặc Xác nhận (nộp).
export function EndConfirm({ busy, error, onBack, onConfirm }: Props) {
  return (
    <Modal title="Kết thúc bài làm?" locked={busy} onClose={onBack}>
      {error && <p role="alert" className="mt-3 text-sm font-bold text-danger">{error}</p>}
      <div className="mt-5 flex gap-3">
        <button type="button" disabled={busy} onClick={onBack} className={pillNeutral}>Làm tiếp</button>
        <button type="button" disabled={busy} onClick={onConfirm} className={pillPrimary}>{busy ? 'Đang nộp…' : 'Xác nhận'}</button>
      </div>
    </Modal>
  )
}
