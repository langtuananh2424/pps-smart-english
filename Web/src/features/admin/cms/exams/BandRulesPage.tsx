import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { btnOutline, btnPrimary, inputCls, Loading, PageHead, RetryError, Section } from '../../ui'
import { examApi, SKILL_LABEL, SKILLS, useBandRules, useExamWrite, usePlacementConfig, type BandRule } from './api'
import { validateRules, type RuleRow } from './rules'
import { ErrorText, Field } from './ui'

const toRow = (r: BandRule): RuleRow => ({ skill: r.skill, minPercent: String(r.minPercent), band: String(r.band) })

function RulesEditor({ initial }: { initial: BandRule[] }) {
  const [rows, setRows] = useState<RuleRow[]>(initial.map(toRow))
  const [draft, setDraft] = useState<RuleRow>({ skill: SKILLS[0], minPercent: '', band: '' })
  const [errors, setErrors] = useState<string[]>([])
  const save = useExamWrite(examApi.putRules)

  const add = (e: FormEvent) => {
    e.preventDefault()
    const next = [...rows, draft]
    const { errors: errs } = validateRules(next)
    setErrors(errs)
    if (errs.length) return
    setRows(next)
    setDraft({ ...draft, minPercent: '', band: '' })
  }
  const submit = () => {
    const { rules, errors: errs } = validateRules(rows)
    setErrors(errs)
    if (!errs.length) save.mutate(rules)
  }

  return (
    <>
      <Section title="Các mốc quy đổi">
        <p className="font-semibold text-ink-secondary">Mỗi kỹ năng: đạt từ mốc % câu đúng trở lên thì được band tương ứng.</p>
        {rows.length === 0 ? (
          <p className="font-semibold text-ink-secondary">Chưa có mốc nào.</p>
        ) : (
          <ul>
            {rows.map((r, i) => (
              <li key={`${r.skill}-${r.minPercent}-${i}`} className="grid grid-cols-[1fr_auto] items-center gap-2 border-b border-line-brand p-2.5 last:border-b-0 md:grid-cols-[1fr_1fr_1fr_auto]">
                <span className="font-extrabold text-ink">{SKILL_LABEL[r.skill] ?? r.skill}</span>
                <span className="font-semibold text-ink-secondary">Từ {r.minPercent}% đúng</span>
                <span className="font-semibold text-ink-secondary">Band {r.band}</span>
                <button type="button" onClick={() => setRows(rows.filter((_, j) => j !== i))} className="min-h-11 font-extrabold text-danger-fg underline" aria-label={`Xóa mốc ${SKILL_LABEL[r.skill] ?? r.skill} ${r.minPercent}%`}>
                  Xóa
                </button>
              </li>
            ))}
          </ul>
        )}
      </Section>
      <Section title="Thêm mốc">
        <form onSubmit={add} className="flex flex-col gap-3 md:flex-row md:items-end">
          <label className="flex min-w-0 flex-1 flex-col gap-2">
            <span className="text-sm font-extrabold text-ink">Kỹ năng</span>
            <select value={draft.skill} onChange={(e) => setDraft({ ...draft, skill: e.target.value })} className={inputCls}>
              {SKILLS.map((s) => (
                <option key={s} value={s}>
                  {SKILL_LABEL[s]}
                </option>
              ))}
            </select>
          </label>
          <Field label="Từ % câu đúng" type="number" min={0} max={100} step={1} value={draft.minPercent} onChange={(e) => setDraft({ ...draft, minPercent: e.target.value })} />
          <Field label="Band" type="number" min={0} max={9} step={0.5} value={draft.band} onChange={(e) => setDraft({ ...draft, band: e.target.value })} />
          <button type="submit" className={btnOutline}>
            Thêm mốc
          </button>
        </form>
      </Section>
      {errors.length > 0 && (
        <ul role="alert" className="font-bold text-danger-fg">
          {errors.map((m) => (
            <li key={m}>{m}</li>
          ))}
        </ul>
      )}
      <ErrorText error={save.error} />
      {save.isSuccess && <p role="status" className="font-bold text-brand-strong">Đã lưu bảng quy đổi.</p>}
      <button type="button" onClick={submit} disabled={save.isPending} className={`${btnPrimary} self-start`}>
        Lưu bảng quy đổi
      </button>
    </>
  )
}

function RetakeConfig({ initial }: { initial: number | null }) {
  const [value, setValue] = useState(initial === null ? '' : String(initial))
  const save = useExamWrite(examApi.putConfig)
  const n = Number(value)
  const valid = value === '' || (Number.isInteger(n) && n >= 0 && n <= 100)
  return (
    <Section title="Số lần làm lại đề đầu vào">
      <Field
        label="Số lần làm lại tối đa"
        type="number"
        min={0}
        max={100}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        hint="Để trống = không cho làm lại."
      />
      <ErrorText error={save.error} />
      {save.isSuccess && <p role="status" className="font-bold text-brand-strong">Đã lưu cấu hình.</p>}
      <button type="button" disabled={!valid || save.isPending} onClick={() => save.mutate(value === '' ? null : n)} className={`${btnPrimary} self-start`}>
        Lưu cấu hình
      </button>
    </Section>
  )
}

// FR-ADM-06, FR-EXAM-03 (Figma 482:17234): bảng quy đổi điểm và cấu hình đề đầu vào.
// Quy đổi band đề thi khóa (D-12) nằm ở màn soạn đề (BankBandRules).
export function BandRulesPage() {
  const rules = useBandRules()
  const config = usePlacementConfig()
  return (
    <>
      <PageHead title="Bảng quy đổi điểm" sub="Cấu hình ánh xạ kết quả sang thang điểm của đề đầu vào." />
      <Link to="/quan-tri/cms/de-thi" className="min-h-11 self-start font-extrabold text-brand-strong underline">
        Quay lại danh sách đề
      </Link>
      {rules.isPending ? <Loading /> : rules.isError ? <RetryError error={rules.error} onRetry={() => rules.refetch()} /> : <RulesEditor initial={rules.data} />}
      {config.isPending ? <Loading /> : config.isError ? <RetryError error={config.error} onRetry={() => config.refetch()} /> : <RetakeConfig initial={config.data.maxRetakes} />}
    </>
  )
}
