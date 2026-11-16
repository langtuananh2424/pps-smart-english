// Tô màu icon SVG của Figma theo currentColor bằng mask.
export function Icon({ src, size = 20, className = '' }: { src: string; size?: number; className?: string }) {
  // url phải có ngoặc kép: Vite nhúng SVG nhỏ thành data URI có khoảng trắng
  const mask = `url("${src}") center / contain no-repeat`
  return (
    <span
      aria-hidden="true"
      className={`inline-block shrink-0 bg-current ${className}`}
      style={{ width: size, height: size, mask, WebkitMask: mask }}
    />
  )
}
