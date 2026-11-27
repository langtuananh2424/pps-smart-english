import { Err, smBtn, smInput } from '../fields'
import { errAt } from '../util'
import { LIMITS } from './layoutModel'
import type { LayoutProps } from './ListLayouts'

// Lưới hàng/cột: ô chứa chữ hoặc chỗ trống {tên}; ô rỗng được phép, tiêu đề cột thì không.
export function TableLayout({ layout, set, errors }: LayoutProps) {
  const header: string[] = layout.header
  const rows: string[][] = layout.rows
  const setHead = (c: number, v: string) => set({ header: header.map((h, j) => (j === c ? v : h)) })
  const setCell = (r: number, c: number, v: string) => set({ rows: rows.map((row, i) => (i === r ? row.map((x, j) => (j === c ? v : x)) : row)) })
  const dropCol = (c: number) => set({ header: header.filter((_, j) => j !== c), rows: rows.map((r) => r.filter((_, j) => j !== c)) })
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-extrabold text-ink">Bảng (chỗ trống viết dạng {'{tên}'})</legend>
      <div className="overflow-x-auto">
        <table className="border-separate border-spacing-1">
          <thead>
            <tr>
              {header.map((h, c) => (
                <th key={c} className="min-w-32 align-top">
                  <input aria-label={`Tiêu đề cột ${c + 1}`} value={h} onChange={(e) => setHead(c, e.target.value)} className={`${smInput} font-extrabold`} />
                  <button type="button" className={`${smBtn} mt-1 w-full`} aria-label={`Xóa cột ${c + 1}`} disabled={header.length <= 1} onClick={() => dropCol(c)}>
                    Xóa cột
                  </button>
                </th>
              ))}
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((row, r) => (
              <tr key={r}>
                {row.map((cell, c) => (
                  <td key={c}>
                    <input aria-label={`Ô hàng ${r + 1} cột ${c + 1}`} value={cell} onChange={(e) => setCell(r, c, e.target.value)} className={smInput} />
                  </td>
                ))}
                <td>
                  <button type="button" className={smBtn} aria-label={`Xóa hàng ${r + 1}`} disabled={rows.length <= 1} onClick={() => set({ rows: rows.filter((_, i) => i !== r) })}>
                    Xóa hàng
                  </button>
                  <Err list={errAt(errors, `layout.rows[${r}]`)} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex gap-2">
        <button type="button" className={smBtn} disabled={rows.length >= LIMITS.rows} onClick={() => set({ rows: [...rows, header.map(() => '')] })}>
          Thêm hàng
        </button>
        <button type="button" className={smBtn} disabled={header.length >= LIMITS.cols} onClick={() => set({ header: [...header, ''], rows: rows.map((r) => [...r, '']) })}>
          Thêm cột
        </button>
      </div>
      <Err list={[...errAt(errors, 'layout.header'), ...(errors['layout.rows'] ? [errors['layout.rows']] : [])]} />
    </fieldset>
  )
}
