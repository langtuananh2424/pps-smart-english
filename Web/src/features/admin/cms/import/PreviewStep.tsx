import { useState } from 'react'
import { Pill } from '../../../checkout/ui'
import { WriteError, btnOutline, btnPrimary, Section } from '../../ui'
import { errorReportCsv, saveBlob, useConfirmImport, type Duplicate, type ImportMode, type ImportResult, type PreviewResult } from './api'

const dupText = (d: Duplicate) =>
  d.scope === 'FILE'
    ? `Trùng dòng ${d.line} trong lần nhập này`
    : d.scope === 'BANK'
      ? `Trùng câu #${d.questionId} trong bộ này`
      : `Trùng câu #${d.questionId} ở bộ "${d.bankTitle}"`

interface Props {
  bankId: string
  preview: PreviewResult
  onBack: () => void
  onDone: (r: ImportResult) => void
}

// Bước 3+4: xem trước từng dòng, bỏ dòng, chọn chế độ rồi xác nhận.
export function PreviewStep({ bankId, preview, onBack, onDone }: Props) {
  const [dropped, setDropped] = useState<Set<number>>(new Set())
  const [onlyErrors, setOnlyErrors] = useState(false)
  const [mode, setMode] = useState<ImportMode>('ALL_OR_NOTHING')
  const confirm = useConfirmImport(bankId)

  const kept = preview.rows.map((r, i) => ({ r, i })).filter((x) => !dropped.has(x.i))
  const valid = kept.filter((x) => x.r.valid && x.r.question != null)
  const invalid = kept.filter((x) => !x.r.valid)
  const shown = onlyErrors ? invalid : kept
  const blocked = valid.length === 0 || (mode === 'ALL_OR_NOTHING' && invalid.length > 0)

  const drop = (i: number) => setDropped((s) => new Set(s).add(i))
  const submit = () =>
    confirm.mutate({ source: preview.source, mode, rows: valid.map((x) => ({ line: x.r.line, question: x.r.question })) }, { onSuccess: onDone })
  const report = () =>
    saveBlob(
      new Blob([errorReportCsv(invalid.map((x) => ({ line: x.r.line, sheet: x.r.sheet, errors: x.r.errors })))], { type: 'text/csv;charset=utf-8' }),
      'bao-cao-loi-nhap.csv',
    )

  return (
    <>
      <Section title="Kết quả kiểm tra" tone="brand">
        <p role="status" className="text-lg font-black text-ink">
          {valid.length} hợp lệ, {invalid.length} lỗi
        </p>
        {preview.warnings.map((w) => (
          <p key={w} className="font-semibold text-ink-secondary">
            {w}
          </p>
        ))}
        <div className="flex flex-wrap gap-2">
          <label className="inline-flex min-h-11 items-center gap-2 font-extrabold">
            <input type="checkbox" checked={onlyErrors} onChange={(e) => setOnlyErrors(e.target.checked)} />
            Chỉ dòng lỗi
          </label>
          {invalid.length > 0 && (
            <button type="button" className={btnOutline} onClick={report}>
              Tải báo cáo lỗi (CSV)
            </button>
          )}
        </div>
      </Section>

      <ul className="flex flex-col gap-2">
        {shown.map(({ r, i }) => (
          <li key={i} className={`flex flex-col gap-1.5 rounded-md border p-3 ${r.valid ? 'border-line-brand bg-surface' : 'border-danger-fg bg-danger-bg'}`}>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-[13px] font-extrabold text-ink-secondary">
                {r.sheet ? `${r.sheet} · ` : ''}Dòng {r.line}
              </span>
              {r.type && <Pill className="bg-muted text-ink-secondary">{r.type}</Pill>}
              <Pill className={r.valid ? 'bg-subtle text-brand-strong' : 'bg-surface text-danger-fg'}>{r.valid ? 'Hợp lệ' : 'Có lỗi'}</Pill>
              <button type="button" className="ml-auto min-h-11 font-extrabold text-brand-strong underline" onClick={() => drop(i)} aria-label={`Bỏ dòng ${r.line}`}>
                Bỏ dòng này
              </button>
            </div>
            {r.prompt && <p className="break-words font-bold text-ink">{r.prompt}</p>}
            {r.errors.map((e, k) => (
              <p key={k} role="alert" className="font-bold text-danger-fg">
                {e.field ? `${e.field}: ` : ''}
                {e.message}
              </p>
            ))}
            {r.duplicates.map((d, k) => (
              <p key={k} className="font-bold text-ink-secondary">
                Cảnh báo: {dupText(d)}
              </p>
            ))}
          </li>
        ))}
        {shown.length === 0 && <li className="font-semibold text-ink-secondary">Không có dòng nào để hiển thị.</li>}
      </ul>

      <Section title="Xác nhận nhập">
        <fieldset className="flex flex-col gap-2">
          <legend className="sr-only">Chế độ nhập</legend>
          <label className="flex items-center gap-2 font-bold">
            <input type="radio" name="mode" checked={mode === 'ALL_OR_NOTHING'} onChange={() => setMode('ALL_OR_NOTHING')} />
            Chỉ nhập khi tất cả hợp lệ
          </label>
          <label className="flex items-center gap-2 font-bold">
            <input type="radio" name="mode" checked={mode === 'SKIP_INVALID'} onChange={() => setMode('SKIP_INVALID')} />
            Bỏ qua dòng lỗi
          </label>
        </fieldset>
        {mode === 'ALL_OR_NOTHING' && invalid.length > 0 && (
          <p className="font-semibold text-ink-secondary">Còn {invalid.length} dòng lỗi: bỏ dòng, sửa văn bản rồi kiểm tra lại, hoặc chọn bỏ qua dòng lỗi.</p>
        )}
        {confirm.isError && <WriteError error={confirm.error} />}
        <div className="flex flex-wrap gap-2">
          <button type="button" className={btnPrimary} disabled={blocked || confirm.isPending} onClick={submit}>
            {confirm.isPending ? 'Đang nhập...' : `Nhập ${valid.length} câu hợp lệ`}
          </button>
          <button type="button" className={btnOutline} onClick={onBack}>
            Chọn lại
          </button>
        </div>
      </Section>
    </>
  )
}
