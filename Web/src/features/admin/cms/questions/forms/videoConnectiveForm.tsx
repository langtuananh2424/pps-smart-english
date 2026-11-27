import { Err } from '../fields'
import { ChoiceEditor } from './choiceForms'
import type { EditorProps } from './props'

// D-159: quiz sau video. Lựa chọn dùng chung biểu mẫu trắc nghiệm; "nhiều đáp án" ghi vào payload.multiple.
// Giải thích tiếng Việt là ô "Giải thích" chung của câu (hiện sau khi chốt), không nằm trong payload vì payload gửi cho học viên.
export function VideoConnectiveEditor(props: EditorProps) {
  const { draft, patch, errors } = props
  const multiple = draft.payload.multiple === true
  const onMultiple = (on: boolean) => {
    const choices = on ? draft.choices : draft.choices.map((c, i) => ({ ...c, isCorrect: c.isCorrect && draft.choices.findIndex((x) => x.isCorrect) === i }))
    patch({ payload: { multiple: on }, choices })
  }
  return (
    <div className="flex flex-col gap-3">
      <label className="flex min-h-10 items-center gap-2 font-extrabold text-ink">
        <input type="checkbox" checked={multiple} onChange={(e) => onMultiple(e.target.checked)} />
        Cho chọn nhiều đáp án
      </label>
      <Err list={errors['payload.multiple'] ? [errors['payload.multiple']] : []} />
      <ChoiceEditor {...props} />
      <p className="text-sm font-semibold text-ink-secondary">Nhóm câu hỏi phải có video (tải mp4 hoặc link YouTube). Một nhóm có thể có nhiều câu quiz.</p>
    </div>
  )
}
