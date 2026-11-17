import art from '../../assets/exercises/practice-topic.webp'

// D-64: Luyện thêm theo Topic chưa làm (FR-EX-07 chờ chốt), nút ở trạng thái "Sắp có".
export function PracticeBanner() {
  return (
    <section aria-label="Luyện thêm theo Topic" className="relative flex flex-col gap-2 overflow-hidden rounded-lg border border-line-subtle bg-reward-bg p-4 shadow-edge-card lg:flex-row lg:items-center lg:gap-6 lg:rounded-[28px] lg:px-6 lg:py-5">
      <img src={art} alt="" className="absolute right-4 top-5 h-16 w-[86px] rounded-sm object-cover lg:inset-y-0 lg:right-[200px] lg:top-0 lg:h-full lg:w-[300px] lg:rounded-none lg:opacity-70" />
      <div className="relative flex-1">
        <h2 className="text-lg font-black leading-6 text-ink">Luyện thêm theo Topic</h2>
        <p className="w-[222px] text-sm font-semibold leading-5 text-ink-secondary lg:w-auto">Ôn lại Level và chủ đề cũ, hoặc thử sức Level mới, khó hơn.</p>
      </div>
      <button
        type="button"
        disabled
        className="relative inline-flex min-h-12 cursor-not-allowed items-center justify-center rounded-md bg-muted px-5 py-2.5 text-lg font-black text-ink-muted"
      >
        Sắp có
      </button>
    </section>
  )
}
