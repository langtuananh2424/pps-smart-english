import { useEffect, useRef, useState } from 'react'

export const formatClock = (sec: number) => {
  const s = Math.max(0, sec)
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

// Đếm ngược cục bộ theo mốc hết giờ tuyệt đối (không trôi khi tab bị làm chậm).
// syncKey đổi (mỗi lần đồng bộ với server) thì đặt lại mốc từ remainingSec; tạm dừng thì đứng yên.
export function useCountdown(remainingSec: number | null | undefined, paused: boolean, syncKey: unknown, onExpire: () => void) {
  const [left, setLeft] = useState<number | null>(remainingSec ?? null)
  const expire = useRef(onExpire)
  expire.current = onExpire

  useEffect(() => {
    if (remainingSec == null) return
    setLeft(remainingSec)
    if (paused) return
    const deadline = Date.now() + remainingSec * 1000
    let fired = false
    const tick = () => {
      const l = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
      setLeft(l)
      if (l === 0 && !fired) {
        fired = true
        clearInterval(id)
        expire.current()
      }
    }
    const id = setInterval(tick, 1000)
    if (remainingSec === 0) tick()
    return () => clearInterval(id)
  }, [remainingSec, paused, syncKey])

  return left
}
