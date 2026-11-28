import { useState } from 'react'
import { small } from './NodeRow'

// Xóa hai bước như NodeRow: bấm "Xóa" rồi "Xác nhận xóa".
export function DeleteButton({ label, pending, onConfirm }: { label: string; pending?: boolean; onConfirm: () => void }) {
  const [confirming, setConfirming] = useState(false)
  return confirming ? (
    <button
      type="button"
      aria-label={`Xác nhận xóa ${label}`}
      className={`${small} !border-danger !text-danger-fg`}
      disabled={pending}
      onClick={() => {
        setConfirming(false)
        onConfirm()
      }}
    >
      Xác nhận xóa
    </button>
  ) : (
    <button type="button" aria-label={`Xóa ${label}`} className={small} onClick={() => setConfirming(true)}>
      Xóa
    </button>
  )
}
