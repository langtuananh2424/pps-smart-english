import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { WriteError, btnOutline, btnPrimary, inputCls, labelCls, Section } from '../../ui'
import {
  MAX_FILE_BYTES,
  TEMPLATE_TYPES,
  downloadTemplate,
  fetchGuide,
  previewImport,
  type ImportInput,
  type PreviewResult,
} from './api'
import { PASTE_SAMPLE } from './samples'

interface Props {
  bankId: string
  text: string
  onText: (t: string) => void
  onPreview: (p: PreviewResult, input: ImportInput) => void
}

const tab = (on: boolean) =>
  `inline-flex min-h-11 items-center rounded-md border px-3.5 font-extrabold ${on ? 'border-brand bg-subtle text-ink' : 'border-line-brand bg-surface text-ink-secondary'}`

// Bước 1+2: chọn cách nhập rồi dán/tải lên; gửi xem trước, chưa ghi câu nào.
export function InputStep({ bankId, text, onText, onPreview }: Props) {
  const [mode, setMode] = useState<'PASTE' | 'EXCEL'>('PASTE')
  const [file, setFile] = useState<File | null>(null)
  const [fileErr, setFileErr] = useState('')
  const [showGuide, setShowGuide] = useState(false)
  const [tplType, setTplType] = useState('')

  const guide = useQuery({ queryKey: ['admin', 'cms', 'import-guide'], queryFn: fetchGuide, enabled: showGuide })
  const tpl = useMutation({ mutationFn: downloadTemplate })
  const preview = useMutation({
    mutationFn: (input: ImportInput) => previewImport(bankId, input),
    onSuccess: (p, input) => onPreview(p, input),
  })

  const pick = (f: File | null) => {
    setFileErr('')
    if (f && !f.name.toLowerCase().endsWith('.xlsx')) return setFileErr('Chỉ nhận file .xlsx.')
    if (f && f.size > MAX_FILE_BYTES) return setFileErr('File quá lớn (tối đa 2 MB).')
    setFile(f)
  }
  const input: ImportInput | null = mode === 'PASTE' ? (text.trim() ? { kind: 'PASTE', text } : null) : file ? { kind: 'EXCEL', file } : null

  return (
    <>
      <nav aria-label="Cách nhập" className="flex flex-wrap gap-2">
        <button type="button" aria-pressed={mode === 'PASTE'} onClick={() => setMode('PASTE')} className={tab(mode === 'PASTE')}>
          Dán nhanh
        </button>
        <button type="button" aria-pressed={mode === 'EXCEL'} onClick={() => setMode('EXCEL')} className={tab(mode === 'EXCEL')}>
          Excel
        </button>
      </nav>

      {mode === 'PASTE' ? (
        <Section title="Dán nội dung câu hỏi">
          <p className="font-semibold text-ink-secondary">
            Hỗ trợ 4 dạng: Trắc nghiệm, Ghép hai vế, Điền từ, Xếp khối. Mỗi dạng mở bằng dòng "# Tên dạng", mỗi câu bắt đầu bằng "Q:".
          </p>
          <label htmlFor="paste" className={labelCls}>
            Nội dung
          </label>
          <textarea id="paste" value={text} onChange={(e) => onText(e.target.value)} rows={14} className={`${inputCls} py-3 font-mono text-sm`} />
          <div className="flex flex-wrap gap-2">
            <button type="button" className={btnOutline} onClick={() => onText(PASTE_SAMPLE)}>
              Chèn ví dụ
            </button>
            <button type="button" className={btnOutline} onClick={() => setShowGuide((v) => !v)}>
              {showGuide ? 'Ẩn hướng dẫn' : 'Xem hướng dẫn'}
            </button>
          </div>
          {showGuide && (
            <div className="rounded-md border border-line-brand bg-muted p-3">
              {guide.isPending && <p role="status">Đang tải...</p>}
              {guide.isError && <p role="alert">Không tải được hướng dẫn.</p>}
              {guide.data && <pre className="whitespace-pre-wrap break-words text-sm font-semibold text-ink">{guide.data}</pre>}
            </div>
          )}
        </Section>
      ) : (
        <Section title="Tải file Excel">
          <p className="font-semibold text-ink-secondary">File .xlsx, tối đa 2 MB; mỗi dạng một sheet đúng mẫu.</p>
          <div className="flex flex-wrap items-end gap-2">
            <div className="flex flex-col gap-2">
              <label htmlFor="tpl" className={labelCls}>
                Dạng của file mẫu
              </label>
              <select id="tpl" value={tplType} onChange={(e) => setTplType(e.target.value)} className={inputCls}>
                {TEMPLATE_TYPES.map((t) => (
                  <option key={t.type} value={t.type}>
                    {t.label}
                  </option>
                ))}
              </select>
            </div>
            <button type="button" className={btnOutline} disabled={tpl.isPending} onClick={() => tpl.mutate(tplType)}>
              Tải mẫu
            </button>
          </div>
          {tpl.isError && <WriteError error={tpl.error} />}
          <label htmlFor="xlsx" className={labelCls}>
            Chọn file
          </label>
          <input id="xlsx" type="file" accept=".xlsx" onChange={(e) => pick(e.target.files?.[0] ?? null)} className="font-semibold" />
          {fileErr && (
            <p role="alert" className="text-sm font-bold text-danger-fg">
              {fileErr}
            </p>
          )}
        </Section>
      )}

      {preview.isError && <WriteError error={preview.error} />}
      <div>
        <button type="button" className={btnPrimary} disabled={!input || preview.isPending} onClick={() => input && preview.mutate(input)}>
          {preview.isPending ? 'Đang kiểm tra...' : 'Kiểm tra dữ liệu'}
        </button>
      </div>
    </>
  )
}
