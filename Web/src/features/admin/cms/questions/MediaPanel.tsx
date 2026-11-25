import { useState } from 'react'
import { labelCls } from '../../ui'
import { Err, smBtn, smInput } from './fields'
import { DISPLAY_SIZES, type MediaDraft } from './types'
import { uploadImage } from './upload'

interface Props {
  media: MediaDraft[]
  onChange: (m: MediaDraft[]) => void
  // Chỉ dạng gõ chữ (FILL_TEXT) có ảnh nền sơ đồ.
  allowDiagram: boolean
  errors: string[]
}

// Ảnh của câu: mô tả (alt) bắt buộc, cỡ hiển thị riêng từng ảnh (PUT /questions/{id}/media khi lưu).
export function MediaPanel({ media, onChange, allowDiagram, errors }: Props) {
  const [error, setError] = useState('')
  const set = (i: number, p: Partial<MediaDraft>) => onChange(media.map((m, j) => (j === i ? { ...m, ...p } : m)))
  const addDiagram = async (f?: File) => {
    if (!f) return
    setError('')
    try {
      const img = await uploadImage(f)
      onChange([...media, { assetId: img.id, url: img.url, role: 'DIAGRAM', displaySize: 'MD', altText: '' }])
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Tải ảnh thất bại.')
    }
  }
  if (!media.length && !allowDiagram) return null
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className={labelCls}>Ảnh của câu</legend>
      {media.map((m, i) => (
        <div key={m.assetId} className="flex flex-wrap items-center gap-2 rounded-sm border border-line-subtle p-2">
          <img src={m.url} alt="" className="size-14 rounded-sm object-cover" />
          <input aria-label={`Mô tả ảnh ${i + 1}`} placeholder="Mô tả ảnh (bắt buộc)" value={m.altText} onChange={(e) => set(i, { altText: e.target.value })} className={`${smInput} min-w-40 flex-1`} maxLength={300} />
          <select aria-label={`Cỡ ảnh ${i + 1}`} value={m.displaySize} onChange={(e) => set(i, { displaySize: e.target.value })} className={`${smInput} w-auto`}>
            {DISPLAY_SIZES.map(([c, l]) => (
              <option key={c} value={c}>
                {l}
              </option>
            ))}
          </select>
          {allowDiagram && (
            <select aria-label={`Vai trò ảnh ${i + 1}`} value={m.role} onChange={(e) => set(i, { role: e.target.value as MediaDraft['role'] })} className={`${smInput} w-auto`}>
              <option value="INLINE">Trong đề</option>
              <option value="DIAGRAM">Ảnh nền sơ đồ</option>
            </select>
          )}
          <button type="button" className={smBtn} aria-label={`Gỡ ảnh ${i + 1}`} onClick={() => onChange(media.filter((_, j) => j !== i))}>
            Gỡ
          </button>
        </div>
      ))}
      {allowDiagram && (
        <label className={`${smBtn} cursor-pointer self-start`}>
          Thêm ảnh sơ đồ
          <input type="file" hidden accept="image/jpeg,image/png,image/webp,image/gif" aria-label="Tệp ảnh sơ đồ" onChange={(e) => { void addDiagram(e.target.files?.[0]); e.target.value = '' }} />
        </label>
      )}
      <Err list={error ? [error, ...errors] : errors} />
    </fieldset>
  )
}
