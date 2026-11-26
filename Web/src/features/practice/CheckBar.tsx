import { pillPrimary } from './questions/ui'
import type { CheckState } from './types'
import { isLocked } from './useCheck'

interface Props {
  state?: CheckState
  canCheck: boolean // đã có câu trả lời và lượt không tạm dừng
  pending: boolean
  onCheck: () => void
}

// D-150: nút Kiểm tra và kết quả Đúng/Cần sửa của câu; không hiện đáp án (xem ở màn kết quả).
export function CheckBar({ state, canCheck, pending, onCheck }: Props) {
  const locked = isLocked(state)
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p role="status" className={`text-sm font-extrabold ${state?.correct ? 'text-success-fg' : 'text-danger-fg'}`}>
        {state?.correct ? 'Đúng rồi!' : state && locked ? 'Cần sửa. Xem đáp án ở màn kết quả' : state ? 'Cần sửa' : ''}
      </p>
      {!locked && (
        <button type="button" onClick={onCheck} disabled={!canCheck || pending} className={`${pillPrimary} !h-11 !text-base`}>
          Kiểm tra
        </button>
      )}
    </div>
  )
}
