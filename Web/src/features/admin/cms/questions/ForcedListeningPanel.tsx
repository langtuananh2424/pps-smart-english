import { useState } from 'react'
import { btnPrimary, Loading, RetryError, Section, WriteError } from '../../ui'
import { send, useCmsWrite, useRule } from './api'
import { Err, Labeled, smInput } from './fields'
import type { GroupView, RuleView } from './types'
import { detailsOf, errAt } from './util'

const num = (v: string) => (v === '' ? 0 : Number(v))

function RuleForm({ bankId, rule, groups }: { bankId: number; rule: RuleView; groups: GroupView[] }) {
  const [playLimit, setPlayLimit] = useState(rule.playLimit)
  const [hintEveryN, setHintEveryN] = useState(rule.hintEveryN)
  const [wrongThreshold, setWrongThreshold] = useState(rule.wrongThreshold)
  const [byPart, setByPart] = useState(rule.thresholdMode === 'PART')
  const [total, setTotal] = useState(rule.passPctTotal)
  const [parts, setParts] = useState<Record<number, number>>(Object.fromEntries(rule.groupThresholds.map((t) => [t.groupId, t.passPct])))

  // D-142: chỉ một kiểu ngưỡng. Theo cả bài thì gửi groupThresholds rỗng để bỏ ngưỡng theo part.
  const save = useCmsWrite(() => {
    const groupThresholds = byPart ? groups.map((g) => ({ groupId: g.id, passPct: parts[g.id] ?? rule.passPctTotal })) : []
    return send<RuleView>('PUT', `/banks/${bankId}/forced-listening`, { playLimit, hintEveryN, wrongThreshold, ...(byPart ? { groupThresholds } : { passPctTotal: total, groupThresholds }) })
  })
  const errors = detailsOf(save.error)
  const field = (id: string, label: string, value: number, set: (n: number) => void, key: string, max?: number) => (
    <Labeled label={label} htmlFor={id}>
      <input id={id} type="number" min={1} max={max} value={value || ''} onChange={(e) => set(num(e.target.value))} className={smInput} />
      <Err list={errAt(errors, key)} />
    </Labeled>
  )
  return (
    <form
      className="flex flex-col gap-3"
      aria-label="Luật Nghe ép"
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate()
      }}
    >
      <div className="grid gap-3 sm:grid-cols-3">
        {field('fl-play', 'Số lần nghe tối đa', playLimit, setPlayLimit, 'playLimit')}
        {field('fl-hint', 'Cứ N lượt làm thì hiện câu sai nhiều', hintEveryN, setHintEveryN, 'hintEveryN')}
        {field('fl-wrong', 'Số lần sai để coi là sai nhiều', wrongThreshold, setWrongThreshold, 'wrongThreshold')}
      </div>
      <Labeled label="Ngưỡng đạt" htmlFor="fl-mode">
        <select id="fl-mode" className={smInput} value={byPart ? 'PART' : 'TOTAL'} onChange={(e) => setByPart(e.target.value === 'PART')}>
          <option value="TOTAL">Theo cả bài</option>
          <option value="PART" disabled={!groups.length}>
            Theo từng part
          </option>
        </select>
      </Labeled>
      {byPart ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {groups.map((g, i) => field(`fl-g${g.id}`, `Ngưỡng đạt (%) · ${g.title || `Nhóm ${i + 1}`}`, parts[g.id] ?? rule.passPctTotal, (n) => setParts({ ...parts, [g.id]: n }), 'groupThresholds', 100))}
        </div>
      ) : (
        field('fl-total', 'Ngưỡng đạt cả bài (%)', total, setTotal, 'passPctTotal', 100)
      )}
      <Err list={errAt(errors, 'groupThresholds')} />
      {save.isError && <WriteError error={save.error} />}
      {save.isSuccess && !save.isPending && <p role="status" className="font-bold text-success-fg">Đã lưu luật.</p>}
      <button type="submit" className={`${btnPrimary} self-start`} disabled={save.isPending}>
        Lưu luật Nghe ép
      </button>
    </form>
  )
}

// FR-EX-10: luật mặc định của bộ; chỉ mở khi bộ ở chế độ phát LOCKED (backend trả 409 nếu không).
export function ForcedListeningPanel({ bankId, groups }: { bankId: number; groups: GroupView[] }) {
  const q = useRule(bankId)
  return (
    <Section title="Luật Nghe ép">
      {q.isPending ? <Loading /> : q.isError ? <RetryError error={q.error} onRetry={() => q.refetch()} /> : <RuleForm bankId={bankId} rule={q.data} groups={groups} />}
    </Section>
  )
}
