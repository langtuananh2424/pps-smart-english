import type { LayoutImage } from '../types'

export interface Marker { key: string; label: string; x: number; y: number }
const pct = (n: number) => Math.min(100, Math.max(0, n))

// Ảnh co giãn theo khung; điểm đặt theo % của ảnh nên đúng vị trí ở mọi cỡ màn hình.
export function ImageStage({ image, alt, markers }: { image?: LayoutImage; alt: string; markers: Marker[] }) {
  if (!image) return <p className="rounded-md bg-muted p-3 text-sm font-semibold text-ink-secondary">Chưa có hình cho phần này.</p>
  return (
    <div className="relative mx-auto w-fit max-w-full">
      <img src={image.url} alt={alt} width={image.width} height={image.height} className="block h-auto max-h-[70dvh] w-auto max-w-full rounded-md" />
      {markers.map((m) => (
        <span key={m.key} aria-hidden style={{ left: `${pct(m.x)}%`, top: `${pct(m.y)}%` }} className="absolute grid size-7 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-brand text-xs font-extrabold text-white ring-2 ring-white">
          {m.label}
        </span>
      ))}
    </div>
  )
}
