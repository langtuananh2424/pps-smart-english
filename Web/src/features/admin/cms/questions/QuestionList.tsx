import { send, useCmsWrite } from './api'
import { smBtn } from './fields'
import { typeLabel, type GroupView, type QuestionDetail } from './types'

export type Selection = { k: 'q'; id: number } | { k: 'newQ' } | { k: 'g'; id: number } | { k: 'newG' }

interface Props {
  questions: QuestionDetail[]
  groups: GroupView[]
  sel: Selection
  onSelect: (s: Selection) => void
}

// Cột trái: nhóm và câu. Đổi thứ tự bằng PATCH sortOrder từng câu (backend chưa có sắp xếp hàng loạt).
export function QuestionList({ questions, groups, sel, onSelect }: Props) {
  const ordered = [...questions].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
  const move = useCmsWrite(async ({ a, b }: { a: QuestionDetail; b: QuestionDetail }) => {
    // Hai câu trùng sortOrder thì hoán đổi vị trí theo chỉ số trong danh sách để thứ tự thật sự đổi.
    const [sa, sb] = a.sortOrder === b.sortOrder ? [ordered.indexOf(b), ordered.indexOf(a)] : [b.sortOrder, a.sortOrder]
    await send('PATCH', `/questions/${a.id}`, { sortOrder: sa })
    await send('PATCH', `/questions/${b.id}`, { sortOrder: sb })
  })

  const row = (q: QuestionDetail, peers: QuestionDetail[]) => {
    const n = ordered.indexOf(q) + 1
    const i = peers.indexOf(q)
    const active = sel.k === 'q' && sel.id === q.id
    return (
      <li key={q.id} className={`flex items-center gap-1 rounded-sm ${active ? 'bg-subtle' : ''}`}>
        <button type="button" aria-current={active} onClick={() => onSelect({ k: 'q', id: q.id })} className="flex min-h-11 min-w-0 flex-1 items-center gap-2 px-2 text-left font-bold text-ink">
          <span className="text-ink-secondary">{n}</span>
          <span className="truncate">{typeLabel(q.type)}</span>
          {q.regradeRequired && <span title="Cần chấm lại" aria-label="Cần chấm lại">⚠</span>}
        </button>
        <button type="button" className={smBtn} aria-label={`Đưa câu ${n} lên`} disabled={i === 0 || move.isPending} onClick={() => move.mutate({ a: q, b: peers[i - 1] })}>
          ↑
        </button>
        <button type="button" className={smBtn} aria-label={`Đưa câu ${n} xuống`} disabled={i === peers.length - 1 || move.isPending} onClick={() => move.mutate({ a: q, b: peers[i + 1] })}>
          ↓
        </button>
      </li>
    )
  }

  const loose = ordered.filter((q) => q.groupId === null || !groups.some((g) => g.id === q.groupId))
  return (
    <nav aria-label="Danh sách câu" className="flex min-w-0 flex-col gap-3 rounded-lg border border-line-brand bg-surface p-3">
      {groups.map((g, i) => {
        const peers = ordered.filter((q) => q.groupId === g.id)
        return (
          <div key={g.id} className="flex flex-col gap-1">
            <button type="button" onClick={() => onSelect({ k: 'g', id: g.id })} className={`min-h-11 rounded-sm px-2 text-left font-black text-brand-strong ${sel.k === 'g' && sel.id === g.id ? 'bg-subtle' : ''}`}>
              {g.title || `Nhóm ${i + 1}`}
            </button>
            <ul>{peers.map((q) => row(q, peers))}</ul>
          </div>
        )
      })}
      <ul>{loose.map((q) => row(q, loose))}</ul>
      {move.isError && <p role="alert" className="text-sm font-bold text-danger-fg">Không đổi được thứ tự, vui lòng thử lại.</p>}
      <div className="flex flex-wrap gap-2">
        <button type="button" className={smBtn} onClick={() => onSelect({ k: 'newQ' })}>
          + Thêm câu
        </button>
        <button type="button" className={smBtn} onClick={() => onSelect({ k: 'newG' })}>
          + Thêm nhóm
        </button>
      </div>
    </nav>
  )
}
