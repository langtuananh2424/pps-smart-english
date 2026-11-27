import { useState, type MouseEvent } from 'react'
import { Err, smBtn, smInput } from '../fields'
import type { Obj } from '../types'
import { uploadImage } from '../upload'
import { errAt, nextId } from '../util'
import { LIMITS } from './layoutModel'
import type { LayoutProps } from './ListLayouts'

const round = (n: number) => Math.min(100, Math.max(0, Math.round(n * 10) / 10))
const nextLetter = (ids: string[]) => [...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'].find((c) => !ids.includes(c)) ?? nextId(ids, 'P')

// DIAGRAM (điểm có chỗ trống) và MAP (điểm có nhãn chữ cái): ảnh nền, bấm lên ảnh để đặt điểm đang chọn (x, y theo %).
export function PointsLayout({ layout, set, errors }: LayoutProps) {
  const map = layout.kind === 'MAP'
  const field = map ? 'id' : 'slot'
  const name = map ? 'options' : 'points'
  const pts: Obj[] = layout[name]
  const [sel, setSel] = useState(0)
  const [up, setUp] = useState('')
  const put = (i: number, p: Obj) => set({ [name]: pts.map((x, j) => (j === i ? { ...x, ...p } : x)) })

  const pick = async (f?: File) => {
    if (!f) return
    setUp('')
    try {
      const img = await uploadImage(f)
      set({ assetId: img.id, image: { url: img.url, width: img.width, height: img.height } })
    } catch (e) {
      setUp(e instanceof Error ? e.message : 'Tải ảnh thất bại.')
    }
  }
  const place = (e: MouseEvent<HTMLDivElement>) => {
    const r = e.currentTarget.getBoundingClientRect()
    if (!pts[sel] || !r.width || !r.height) return
    put(sel, { x: round(((e.clientX - r.left) / r.width) * 100), y: round(((e.clientY - r.top) / r.height) * 100) })
  }
  const add = () => {
    const ids = pts.map((p) => String(p[field]))
    set({ [name]: [...pts, { [field]: map ? nextLetter(ids) : nextId(ids, 'p'), x: 50, y: 50 }] })
    setSel(pts.length)
  }

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-extrabold text-ink">{map ? 'Ảnh bản đồ / mặt bằng' : 'Ảnh sơ đồ'}</legend>
      <label className={`${smBtn} cursor-pointer self-start`}>
        {layout.image ? 'Đổi ảnh' : 'Tải ảnh lên'}
        <input type="file" hidden accept="image/*" aria-label="Tệp ảnh bố cục" onChange={(e) => { void pick(e.target.files?.[0]); e.target.value = '' }} />
      </label>
      <Err list={[...(up ? [up] : []), ...errAt(errors, 'layout.assetId')]} />
      {layout.image && (
        <div data-testid="points-canvas" className="relative max-w-xl cursor-crosshair self-start" onClick={place}>
          <img src={layout.image.url} alt="Ảnh nền" className="block w-full select-none" draggable={false} />
          {pts.map((p, i) => (
            <span
              key={i}
              style={{ left: `${p.x}%`, top: `${p.y}%` }}
              className={`absolute flex size-6 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full text-xs font-black ${i === sel ? 'bg-brand text-white' : 'bg-surface text-brand-strong ring-2 ring-brand'}`}
            >
              {p[field]}
            </span>
          ))}
        </div>
      )}
      <p className="text-sm font-semibold text-ink-secondary">Chọn một điểm rồi bấm lên ảnh để đặt vị trí (hoặc nhập x, y theo %).</p>
      {pts.map((p, i) => (
        <div key={i} className="flex flex-wrap items-center gap-2">
          <label className="flex items-center gap-1 font-bold text-ink">
            <input type="radio" name="point" checked={sel === i} onChange={() => setSel(i)} aria-label={`Chọn điểm ${i + 1}`} />
          </label>
          <input aria-label={map ? `Nhãn điểm ${i + 1}` : `Chỗ trống điểm ${i + 1}`} value={p[field]} onChange={(e) => put(i, { [field]: e.target.value })} className={`${smInput} max-w-28`} />
          {(['x', 'y'] as const).map((k) => (
            <input key={k} type="number" min={0} max={100} step={0.1} aria-label={`${k} điểm ${i + 1}`} value={p[k]} onChange={(e) => put(i, { [k]: round(Number(e.target.value)) })} className={`${smInput} max-w-24`} />
          ))}
          <button type="button" className={smBtn} aria-label={`Xóa điểm ${i + 1}`} disabled={pts.length <= (map ? 2 : 0)} onClick={() => { set({ [name]: pts.filter((_, j) => j !== i) }); setSel(0) }}>
            Xóa
          </button>
          <Err list={errAt(errors, `layout.${name}[${i}]`)} />
        </div>
      ))}
      <button type="button" className={`${smBtn} self-start`} disabled={pts.length >= (map ? LIMITS.options : LIMITS.points)} onClick={add}>
        Thêm điểm
      </button>
      <Err list={errors[`layout.${name}`] ? [errors[`layout.${name}`]] : []} />
    </fieldset>
  )
}
