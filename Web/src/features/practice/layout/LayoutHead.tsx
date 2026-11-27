import type { GroupLayout } from '../types'
import { panel, panelTitle } from '../questions/ui'
import { ImageStage } from './ImageStage'

// Bố cục theo danh sách (OPTIONS, MAP): hiện một lần ở đầu nhóm, mỗi câu tự có ô chọn riêng.
export const isHeadLayout = (l?: GroupLayout | null): l is Extract<GroupLayout, { kind: 'OPTIONS' | 'MAP' }> => l?.kind === 'OPTIONS' || l?.kind === 'MAP'

export function LayoutHead({ layout }: { layout: Extract<GroupLayout, { kind: 'OPTIONS' | 'MAP' }> }) {
  if (layout.kind === 'MAP') {
    return (
      <section aria-label={layout.title ?? 'Bản đồ'} className={panel}>
        {layout.title && <h3 className={panelTitle}>{layout.title}</h3>}
        <ImageStage image={layout.image} alt={layout.title ?? 'Bản đồ'} markers={layout.options.map((o) => ({ key: o.id, label: o.id, x: o.x, y: o.y }))} />
      </section>
    )
  }
  return (
    <section aria-label={layout.title ?? 'Danh sách lựa chọn'} className={panel}>
      {layout.title && <h3 className={panelTitle}>{layout.title}</h3>}
      <ul className="flex flex-col gap-1.5">
        {layout.options.map((o) => (
          <li key={o.id} className="flex gap-2 text-base font-semibold text-ink">
            <span className="w-6 shrink-0 font-extrabold text-brand-strong">{o.id}</span>
            <span>{o.text}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
