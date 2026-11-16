// Khung chờ và thông báo lỗi nhỏ cho từng khối.
export const Skeleton = ({ className = '' }: { className?: string }) => (
  <div aria-hidden="true" className={`animate-pulse rounded-md bg-muted ${className}`} />
)

export function BlockError({ onRetry, className = '' }: { onRetry: () => void; className?: string }) {
  return (
    <p role="alert" className={`text-sm font-bold text-ink-secondary ${className}`}>
      Không tải được phần này.{' '}
      <button type="button" onClick={onRetry} className="min-h-11 font-extrabold text-brand-strong underline">
        Thử lại
      </button>
    </p>
  )
}
