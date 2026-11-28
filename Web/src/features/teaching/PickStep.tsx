import { useState } from 'react'
import { inputCls, labelCls, Section } from '../admin/ui'
import type { Assignable } from './api'
import { ASG_MODE_LABEL } from './format'
import { TYPE_WORD, type Picked } from './pick'

// B1 (Figma 480:13365): chọn bài đã xuất bản, nhóm theo Topic; bộ câu và câu Viết/Nói.
export function PickStep({ tree, pick, onPick }: { tree: Assignable; pick: Picked | null; onPick: (p: Picked) => void }) {
  const [kw, setKw] = useState('')
  const k = kw.trim().toLowerCase()
  const has = (t: string) => t.toLowerCase().includes(k)
  const groups = tree.topics
    .map((t) => ({ t, banks: t.banks.filter((b) => has(b.title)), ai: t.aiQuestions.filter((q) => has(q.title)) }))
    .filter((g) => g.banks.length + g.ai.length > 0)
  const row = (key: string, label: string, hint: string, p: Picked) => (
    <li key={key}>
      <label className="flex min-h-11 cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 font-bold text-ink has-[:checked]:bg-subtle">
        <input type="radio" name="pick" checked={pick?.key === key} onChange={() => onPick(p)} className="size-5 accent-brand-strong" />
        <span className="min-w-0 flex-1 break-words">{label}</span>
        <span className="text-sm font-semibold text-ink-secondary">{hint}</span>
      </label>
    </li>
  )
  return (
    <>
      <Section title="Tìm bài">
        <label htmlFor="pick-q" className={labelCls}>Tên bài</label>
        <input id="pick-q" value={kw} onChange={(e) => setKw(e.target.value)} placeholder="Tìm theo tên" className={inputCls} />
      </Section>
      <Section title="Topic và bài">
        {groups.length === 0 && <p className="font-semibold text-ink-secondary">Không có bài phù hợp.</p>}
        {groups.map(({ t, banks, ai }) => (
          <div key={t.topicId} role="group" aria-label={t.title} className="flex flex-col gap-1">
            <h3 className="font-black text-ink">{t.title}</h3>
            <ul className="flex flex-col">
              {banks.map((b) => row(`b${b.bankId}`, b.title, `${ASG_MODE_LABEL[b.kind]} · ${b.questionCount} câu`, { key: `b${b.bankId}`, bankId: b.bankId, kind: b.kind, title: b.title }))}
              {ai.map((q) => row(`q${q.questionId}`, q.title, TYPE_WORD[q.type], { key: `q${q.questionId}`, questionId: q.questionId, title: q.title, type: q.type }))}
            </ul>
          </div>
        ))}
      </Section>
    </>
  )
}
