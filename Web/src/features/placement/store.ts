import type { Saved } from './types'

const CURRENT = 'pps.placement.current'
const keyOf = (id: number) => `pps.placement.${id}`

// localStorage có thể lỗi (chế độ riêng tư, đầy bộ nhớ): bỏ qua, màn vẫn chạy nhờ draft trên server.
export function saveLocal(s: Saved) {
  try {
    localStorage.setItem(keyOf(s.attemptId), JSON.stringify(s))
    localStorage.setItem(CURRENT, String(s.attemptId))
  } catch { /* không lưu được */ }
}

export function loadLocal(): Saved | null {
  try {
    const id = localStorage.getItem(CURRENT)
    const raw = id && localStorage.getItem(keyOf(Number(id)))
    const s = raw ? (JSON.parse(raw) as Saved) : null
    return s && s.attemptId === Number(id) ? s : null
  } catch {
    return null
  }
}

export function clearLocal(attemptId: number) {
  try {
    localStorage.removeItem(keyOf(attemptId))
    if (localStorage.getItem(CURRENT) === String(attemptId)) localStorage.removeItem(CURRENT)
  } catch { /* không xóa được */ }
}
