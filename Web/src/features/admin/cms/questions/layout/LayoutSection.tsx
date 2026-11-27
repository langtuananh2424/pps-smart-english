import { Err, Labeled, smInput } from '../fields'
import type { Obj } from '../types'
import { errAt, type Details } from '../util'
import { emptyLayout, LAYOUT_KINDS, type LayoutKind } from './layoutModel'
import { FlowLayout, FormLayout, NoteLayout, OptionsLayout, TextBankLayout, TextLayout, type LayoutProps } from './ListLayouts'
import { PointsLayout } from './PointsLayout'
import { TableLayout } from './TableLayout'

const EDITORS: Record<LayoutKind, (p: LayoutProps) => React.JSX.Element> = {
  OPTIONS: OptionsLayout,
  TEXT: TextLayout,
  TEXT_BANK: TextBankLayout,
  NOTE: NoteLayout,
  TABLE: TableLayout,
  FLOW: FlowLayout,
  FORM: FormLayout,
  DIAGRAM: PointsLayout,
  MAP: PointsLayout,
}

interface Props {
  value: Obj | null
  onChange: (v: Obj | null) => void
  errors: Details
}

// Bố cục dùng chung của nhóm (D-154): câu IELTS/nghe điền tham chiếu chỗ trống hoặc lựa chọn của bố cục này.
export function LayoutSection({ value, onChange, errors }: Props) {
  const Editor = value ? EDITORS[value.kind as LayoutKind] : null
  const change = (k: string) => {
    if (k === (value?.kind ?? '')) return
    if (value && !window.confirm('Đổi kiểu bố cục sẽ xóa bố cục hiện tại. Tiếp tục?')) return
    onChange(k ? emptyLayout(k as LayoutKind) : null)
  }
  return (
    <fieldset className="flex flex-col gap-3 rounded-sm border border-line-subtle p-3">
      <legend className="px-1 text-sm font-extrabold text-ink">Bố cục dùng chung (IELTS, nghe điền phiếu)</legend>
      <Labeled label="Kiểu bố cục" htmlFor="g-layout-kind">
        <select id="g-layout-kind" value={value?.kind ?? ''} onChange={(e) => change(e.target.value)} className={smInput}>
          <option value="">Không có</option>
          {LAYOUT_KINDS.map(([c, l]) => (
            <option key={c} value={c}>
              {l}
            </option>
          ))}
        </select>
        <Err list={[...errAt(errors, 'layout.kind'), ...(errors.layout ? [errors.layout] : [])]} />
      </Labeled>
      {value && Editor && (
        <>
          <Labeled label="Tiêu đề bố cục (không bắt buộc)" htmlFor="g-layout-title">
            <input id="g-layout-title" maxLength={200} value={value.title ?? ''} onChange={(e) => onChange({ ...value, title: e.target.value })} className={smInput} />
            <Err list={errAt(errors, 'layout.title')} />
          </Labeled>
          <Editor layout={value} set={(p) => onChange({ ...value, ...p })} errors={errors} />
        </>
      )}
    </fieldset>
  )
}
