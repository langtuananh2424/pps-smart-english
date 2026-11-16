import checkWhite from '../../assets/icons/check-white.svg'

interface Props {
  label: string
  desc?: string
  icon?: string
  tile?: string
  selected: boolean
  onClick: () => void
}

// Thẻ chọn theo Figma: icon, tên, mô tả, vòng tròn chọn ở góc phải trên.
export function OptionCard({ label, desc, icon, tile, selected, onClick }: Props) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={`relative flex min-h-11 items-center gap-3.5 py-3.5 pl-3.5 pr-12 text-left lg:flex-col lg:items-start lg:gap-3 lg:py-5 lg:pl-5 ${
        selected ? 'rounded-md border-2 border-brand bg-page' : 'rounded-md border-2 border-line bg-surface shadow-edge-card lg:rounded-lg lg:border'
      }`}
    >
      {icon && (
        <span className={`grid size-[46px] place-items-center rounded-sm ${tile}`}>
          <img src={icon} alt="" className="size-6" />
        </span>
      )}
      <span className="flex flex-col gap-[3px]">
        <span className="text-base font-black leading-6 text-ink lg:text-lg">{label}</span>
        {desc && <span className="text-sm font-semibold leading-5 text-ink-secondary">{desc}</span>}
      </span>
      <span
        aria-hidden="true"
        className={`absolute right-3 top-1/2 grid size-6 -translate-y-1/2 lg:top-3 lg:translate-y-0 place-items-center rounded-full ${selected ? 'bg-brand' : 'border-2 border-line bg-surface'}`}
      >
        {selected && <img src={checkWhite} alt="" className="size-3.5" />}
      </span>
    </button>
  )
}
