import { useState } from 'react'
import { btnOutline, btnPrimary, inputCls, Loading, RetryError, Section } from '../../ui'
import { detailsOf } from '../questions/util'
import { BANK_SKILLS, examApi, SKILL_LABEL, useBankBandRules, useExamWrite, type BandRule } from './api'
import { ErrorText } from './ui'

interface Row {
  skill: string
  minPercent: string
  band: string
}
const toRow = (r: BandRule): Row => ({ ...r, minPercent: String(r.minPercent), band: String(r.band) })

// Lỗi 400: khóa "rules[i].trường" gắn vào dòng i của danh sách phẳng; lỗi trùng ("... <kỹ năng> <mốc %>") gắn vào dòng trùng đầu tiên.
function errorsByRow(error: unknown, flat: Row[]): { byRow: Map<number, string[]>; general: string[] } {
  const byRow = new Map<number, string[]>()
  const general: string[] = []
  for (const [k, v] of Object.entries(detailsOf(error))) {
    const msg = String(v)
    const idx = /^rules\[(\d+)\]/.exec(k)
    const dup = /(\w+) (\d+)$/.exec(msg)
    const at = idx ? Number(idx[1]) : dup ? flat.findIndex((r) => r.skill === dup[1] && r.minPercent === dup[2]) : -1
    if (at >= 0) byRow.set(at, [...(byRow.get(at) ?? []), msg])
    else general.push(msg)
  }
  return { byRow, general }
}

const Alert = ({ text }: { text: string }) => (
  <p role="alert" className="font-bold text-danger-fg">
    {text}
  </p>
)

function Editor({ bankId, initial }: { bankId: number; initial: BandRule[] }) {
  const [rows, setRows] = useState<Row[]>(initial.map(toRow))
  const [clientErr, setClientErr] = useState('')
  const save = useExamWrite((rules: BandRule[]) => examApi.putBankRules(bankId, rules))
  // Thứ tự gửi = nhóm theo kỹ năng; chỉ số lỗi của BE khớp thứ tự này.
  const flat = BANK_SKILLS.flatMap((s) => rows.filter((r) => r.skill === s))
  const { byRow, general } = errorsByRow(save.error, flat)
  const hasDetails = Object.keys(detailsOf(save.error)).length > 0
  // Mọi thay đổi bảng làm chỉ số lỗi cũ lệch dòng, nên xóa lỗi đã nhận.
  const edit = (next: Row[]) => {
    save.reset()
    setRows(next)
  }
  const patch = (r: Row, p: Partial<Row>) => edit(rows.map((x) => (x === r ? { ...x, ...p } : x)))

  const submit = () => {
    const rules = flat.map((r) => ({ skill: r.skill, minPercent: Number(r.minPercent), band: Number(r.band.replace(',', '.')) }))
    if (flat.some((r, i) => r.minPercent.trim() === '' || r.band.trim() === '' || Number.isNaN(rules[i].minPercent) || Number.isNaN(rules[i].band))) {
      setClientErr('Mỗi dòng cần nhập % tối thiểu và band.')
      return
    }
    if (rules.some((r) => !Number.isInteger(r.minPercent) || r.minPercent < 0 || r.minPercent > 100)) {
      setClientErr('Mốc % là số nguyên từ 0 đến 100.')
      return
    }
    if (rules.some((r) => r.band < 0 || r.band > 9 || (r.band * 2) % 1 !== 0)) {
      setClientErr('Band từ 0 đến 9, bước 0,5.')
      return
    }
    if (rules.some((r, i) => rules.findIndex((x) => x.skill === r.skill && x.minPercent === r.minPercent) !== i)) {
      setClientErr('Mỗi kỹ năng không được có hai dòng cùng mốc %.')
      return
    }
    setClientErr('')
    save.mutate(rules)
  }

  return (
    <>
      {BANK_SKILLS.map((s) => (
        <div key={s} className="flex flex-col gap-2">
          <h3 className="font-extrabold text-ink">{SKILL_LABEL[s]}</h3>
          {flat.map((r, i) => {
            if (r.skill !== s) return null
            const n = flat.slice(0, i).filter((x) => x.skill === s).length + 1
            return (
              <div key={`${s}-${i}`} className="flex flex-col gap-1">
                <div className="flex flex-wrap items-center gap-2">
                  <label className="flex items-center gap-2 font-semibold text-ink-secondary">
                    Từ %
                    <input type="number" min={0} max={100} step={1} value={r.minPercent} onChange={(e) => patch(r, { minPercent: e.target.value })} aria-label={`${SKILL_LABEL[s]} dòng ${n} từ %`} className={`${inputCls} w-28`} />
                  </label>
                  <label className="flex items-center gap-2 font-semibold text-ink-secondary">
                    Band
                    <input type="number" min={0} max={9} step={0.5} value={r.band} onChange={(e) => patch(r, { band: e.target.value })} aria-label={`${SKILL_LABEL[s]} dòng ${n} band`} className={`${inputCls} w-24`} />
                  </label>
                  <button type="button" onClick={() => edit(rows.filter((x) => x !== r))} className="min-h-11 font-extrabold text-danger-fg underline" aria-label={`Xóa dòng ${n} ${SKILL_LABEL[s]}`}>
                    Xóa
                  </button>
                </div>
                {byRow.get(i)?.map((m) => <Alert key={m} text={m} />)}
              </div>
            )
          })}
          <button type="button" onClick={() => edit([...rows, { skill: s, minPercent: '', band: '' }])} className={`${btnOutline} self-start`}>
            Thêm mốc {SKILL_LABEL[s]}
          </button>
        </div>
      ))}
      {clientErr && <Alert text={clientErr} />}
      {general.map((m) => <Alert key={m} text={m} />)}
      {/* Lỗi có chi tiết đã hiện ở dòng; ErrorText chỉ cho lỗi không có chi tiết (403, mạng). */}
      {!hasDetails && <ErrorText error={save.error} />}
      {save.isSuccess && (
        <p role="status" className="font-bold text-brand-strong">
          Đã lưu bảng quy đổi.
        </p>
      )}
      <button type="button" onClick={submit} disabled={save.isPending} className={`${btnPrimary} self-start`}>
        Lưu bảng quy đổi
      </button>
    </>
  )
}

// FR-EXAM-03, FR-ADM-06, D-12, D-167 (Figma "CMS đề thi · Quy đổi điểm"): mỗi kỹ năng một bảng "% điểm tối thiểu -> band". Bảng rỗng = xóa bảng.
export function BankBandRules({ bankId }: { bankId: number }) {
  const q = useBankBandRules(bankId)
  return (
    <Section title="Bảng quy đổi band của đề">
      {q.isPending ? <Loading /> : q.isError ? <RetryError error={q.error} onRetry={() => q.refetch()} /> : <Editor key={JSON.stringify(q.data)} bankId={bankId} initial={q.data} />}
    </Section>
  )
}
