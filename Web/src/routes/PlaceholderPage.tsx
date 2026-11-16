export function PlaceholderPage({ title }: { title: string }) {
  return (
    <>
      <h1 className="text-2xl font-black text-ink">{title}</h1>
      <p className="mt-2 font-semibold text-ink-secondary">Đang xây dựng.</p>
    </>
  )
}
