import { useState } from 'react'
import { Link } from 'react-router'
import cam from '../../assets/home/cam-kinh.png'
import { useCamMessages } from './api'

const KEY = 'cam-bubble-dismissed'
const read = () => {
  try {
    return sessionStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}
const write = (dismissed: boolean) => {
  try {
    if (dismissed) sessionStorage.setItem(KEY, '1')
    else sessionStorage.removeItem(KEY)
  } catch {
    /* sessionStorage có thể bị chặn; chỉ mất việc nhớ trong phiên */
  }
}

// Dưới lg mặc định thu gọn như Figma mobile; môi trường không có matchMedia coi như desktop.
const isDesktop = () => typeof matchMedia !== 'function' || matchMedia('(min-width: 1024px)').matches

const nav = 'grid size-11 place-items-center rounded-sm bg-input text-lg font-extrabold text-ink'

// Bong bóng Cam góc dưới phải: tin xem lần lượt bằng nút ‹ ›, không tự chuyển.
export function CamBubble() {
  const { data } = useCamMessages()
  const [open, setOpen] = useState(() => isDesktop() && !read())
  const [i, setI] = useState(0)
  const messages = data?.messages ?? []
  if (messages.length === 0) return null
  const msg = messages[Math.min(i, messages.length - 1)]
  const set = (v: boolean) => {
    setOpen(v)
    write(!v)
  }

  return (
    <div className="fixed bottom-20 right-3 z-20 flex max-w-[calc(100vw-2rem)] flex-col items-end gap-2 md:bottom-4 md:right-4">
      {open && (
        <section aria-label="Gợi ý từ Cam" className="w-[300px] max-w-full rounded-md border border-line-subtle bg-surface p-3 shadow-edge-card">
          <div className="flex items-center justify-between">
            <span className="text-[15px] font-extrabold text-brand-strong">Cam</span>
            <button type="button" aria-label="Đóng gợi ý của Cam" onClick={() => set(false)} className={`${nav} text-[13px]`}>
              ×
            </button>
          </div>
          <p className="text-sm font-semibold leading-5 text-ink">{msg.text}</p>
          {msg.action && (
            <Link to={msg.action.path} className="mt-2 inline-flex min-h-11 items-center text-sm font-extrabold text-brand-strong underline">
              {msg.action.label}
            </Link>
          )}
          {messages.length > 1 && (
            <div className="mt-1 flex items-center justify-between">
              <button type="button" aria-label="Tin trước" onClick={() => setI((i - 1 + messages.length) % messages.length)} className={nav}>
                ‹
              </button>
              <span className="text-xs font-bold text-ink-secondary">
                {Math.min(i, messages.length - 1) + 1}/{messages.length}
              </span>
              <button type="button" aria-label="Tin sau" onClick={() => setI((i + 1) % messages.length)} className={nav}>
                ›
              </button>
            </div>
          )}
        </section>
      )}
      <button
        type="button"
        aria-label={open ? 'Ẩn gợi ý của Cam' : 'Mở gợi ý của Cam'}
        aria-expanded={open}
        onClick={() => set(!open)}
        className="size-[60px] overflow-hidden rounded-full border border-line bg-surface p-0.5 md:size-[76px]"
      >
        <img src={cam} alt="" className="size-full object-contain" />
      </button>
    </div>
  )
}
