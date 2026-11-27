import type { ChoiceNote } from './types'

// FR-EX-03: giải thích từng phương án; máy chủ chỉ trả khi đã lộ đáp án.
export function ChoiceNotes({ notes }: { notes?: ChoiceNote[] | null }) {
  if (!notes?.length) return null
  return (
    <ul aria-label="Giải thích từng phương án" className="flex flex-col gap-1 text-sm font-semibold">
      {notes.map((n) => (
        <li key={n.label} className="flex gap-2">
          <span className={`shrink-0 font-extrabold ${n.correct ? 'text-success-fg' : 'text-danger-fg'}`}>{n.label}.</span>
          <span className="text-ink">{n.explanation}</span>
        </li>
      ))}
    </ul>
  )
}
