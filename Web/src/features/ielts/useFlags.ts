import { useCallback, useState } from 'react'

// Câu đánh dấu xem lại: lưu theo lượt để tải lại trang không mất (chỉ là ghi chú của học viên, không gửi lên máy chủ).
export function useFlags(attemptId: number) {
  const key = `ielts:flags:${attemptId}`
  const [flags, setFlags] = useState<Set<number>>(() => {
    try {
      return new Set(JSON.parse(sessionStorage.getItem(key) ?? '[]') as number[])
    } catch {
      return new Set()
    }
  })
  const toggle = useCallback((id: number) => setFlags((prev) => {
    const next = new Set(prev)
    if (!next.delete(id)) next.add(id)
    try { sessionStorage.setItem(key, JSON.stringify([...next])) } catch { /* bộ nhớ đầy hoặc bị chặn: bỏ qua */ }
    return next
  }), [key])
  const clear = useCallback(() => { try { sessionStorage.removeItem(key) } catch { /* bỏ qua */ } }, [key])
  return { flags, toggle, clear }
}
