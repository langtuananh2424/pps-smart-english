import type { BankGroup } from './types'

// Bài đọc dùng chung của nhóm câu (Figma 10.15).
export function GroupPassage({ group }: { group: BankGroup }) {
  if (!group.passageHtml && !group.title) return null
  return (
    <section className="flex flex-col gap-2 rounded-md bg-subtle p-4">
      <h2 className="text-xs font-extrabold uppercase tracking-wide text-brand-strong">{group.title ?? 'Đoạn đọc dùng chung'}</h2>
      {/* passageHtml do máy chủ đã làm sạch */}
      {group.passageHtml && <div className="text-base leading-7 text-ink" dangerouslySetInnerHTML={{ __html: group.passageHtml }} />}
    </section>
  )
}
