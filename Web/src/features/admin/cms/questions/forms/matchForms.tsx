import { IdTextList, Labeled, smInput, type IdText } from '../fields'
import type { Obj } from '../types'
import { ScoringSelect } from './choiceForms'
import type { EditorProps } from './props'

// Giữ lại chỉ các cặp còn trỏ tới id tồn tại (sau khi xóa phần tử).
const prune = (map: Obj, keys: IdText[], vals: IdText[]): Obj =>
  Object.fromEntries(Object.entries(map).filter(([k, v]) => keys.some((x) => x.id === k) && vals.some((x) => x.id === v)))

interface PairProps {
  items: IdText[]
  options: IdText[]
  map: Obj
  onChange: (m: Obj) => void
  label: string
}

function PairPicker({ items, options, map, onChange, label }: PairProps) {
  return (
    <div className="flex flex-col gap-2">
      {items.map((it, i) => (
        <Labeled key={it.id} label={`${label} ${i + 1}: ${it.text || '(trống)'}`} htmlFor={`pair-${it.id}`}>
          <select id={`pair-${it.id}`} className={smInput} value={map[it.id] ?? ''} onChange={(e) => onChange({ ...map, [it.id]: e.target.value })}>
            <option value="">Chọn đáp án</option>
            {options.map((o, j) => (
              <option key={o.id} value={o.id}>
                {j + 1}. {o.text || '(trống)'}
              </option>
            ))}
          </select>
        </Labeled>
      ))}
    </div>
  )
}

// Ghép hai vế: mỗi vế trái một vế phải; vế phải có thể thừa (nhiễu).
export function MatchEditor({ draft, patch }: EditorProps) {
  const left: IdText[] = draft.payload.left ?? []
  const right: IdText[] = draft.payload.right ?? []
  const pairs: Obj = draft.answerKey.pairs ?? {}
  const apply = (l: IdText[], r: IdText[]) => patch({ payload: { left: l, right: r }, answerKey: { pairs: prune(pairs, l, r) } })
  return (
    <div className="flex flex-col gap-4">
      <div className="grid gap-4 md:grid-cols-2">
        <IdTextList label="Vế trái" prefix="l" items={left} onChange={(l) => apply(l, right)} />
        <IdTextList label="Vế phải" prefix="r" min={2} items={right} onChange={(r) => apply(left, r)} />
      </div>
      <PairPicker label="Vế trái" items={left} options={right} map={pairs} onChange={(m) => patch({ answerKey: { pairs: m } })} />
    </div>
  )
}

// Chọn mục từ danh sách chung (Matching headings/information...).
export function MatchOptionsEditor({ draft, patch }: EditorProps) {
  const items: IdText[] = draft.payload.items ?? []
  const options: IdText[] = draft.payload.options ?? []
  const matches: Obj = draft.answerKey.matches ?? {}
  const apply = (it: IdText[], op: IdText[]) =>
    patch({ payload: { ...draft.payload, items: it, options: op }, answerKey: { matches: prune(matches, it, op) } })
  return (
    <div className="flex flex-col gap-4">
      <ScoringSelect id="scoring" value={draft.payload.scoring} onChange={(scoring) => patch({ payload: { ...draft.payload, scoring } })} />
      <div className="grid gap-4 md:grid-cols-2">
        <IdTextList label="Mục" prefix="i" items={items} onChange={(it) => apply(it, options)} />
        <IdTextList label="Lựa chọn chung" prefix="o" min={2} items={options} onChange={(op) => apply(items, op)} />
      </div>
      <PairPicker label="Mục" items={items} options={options} map={matches} onChange={(m) => patch({ answerKey: { matches: m } })} />
    </div>
  )
}
