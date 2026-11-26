import { useNavigate } from 'react-router'
import cam from '../../assets/home/cam-kinh.png'
import { useGradedNotice, useMarkRead } from './api'

// Chỉ nhận đường dẫn nội bộ: "//host" và "/\\host" là URL ngoài (chống open redirect).
const isInternal = (u?: string): u is string => !!u && /^\/(?![/\\])/.test(u)

// D-149: Cam báo bài đã chấm xong ở mọi trang khu học viên. Màn làm bài nằm ngoài AppShell nên không hiện ở đó.
export function GradedBubble() {
  const n = useGradedNotice()
  const read = useMarkRead()
  const navigate = useNavigate()
  if (!n) return null
  const url = n.payload.url
  const go = () => {
    read.mutate(n.id)
    if (isInternal(url)) navigate(url)
  }
  return (
    <aside aria-label="Cam báo kết quả" className="fixed bottom-20 right-3 z-20 flex max-w-[calc(100vw-2rem)] flex-col items-end gap-2 md:bottom-4 md:right-4">
      <section className="w-[300px] max-w-full rounded-md border border-line-subtle bg-surface p-3 shadow-edge-card">
        <div className="flex items-center justify-between">
          <span className="text-[15px] font-extrabold text-brand-strong">Cam</span>
          <button type="button" aria-label="Đóng thông báo của Cam" disabled={read.isPending} onClick={() => read.mutate(n.id)} className="grid size-11 place-items-center rounded-sm bg-input text-[13px] font-extrabold text-ink">
            ×
          </button>
        </div>
        <p className="text-sm font-semibold leading-5 text-ink">{n.payload.title}</p>
        <button type="button" disabled={read.isPending} onClick={go} className="mt-2 inline-flex min-h-11 items-center text-sm font-extrabold text-brand-strong underline">
          Xem kết quả →
        </button>
      </section>
      <img src={cam} alt="" className="size-[60px] rounded-full border border-line bg-surface p-0.5 md:size-[76px]" />
    </aside>
  )
}
