import type { Details } from '../util'
import type { Draft, GrammarElement, Obj } from '../types'

export interface EditorProps {
  draft: Draft
  patch: (p: Partial<Draft>) => void
  errors: Details
  elements: GrammarElement[]
  // Layout của nhóm đang chọn (D-154); các dạng IELTS/nghe điền lấy chỗ trống và lựa chọn từ đây.
  layout: Obj | null
}
