import { useEffect, useState } from 'react'

// Nội dung đang gõ giữ trong sessionStorage theo phiên để reload không mất. Lỗi storage thì bỏ qua.
const read = (key: string) => { try { return sessionStorage.getItem(key) } catch { return null } }
const write = (key: string, v: string) => { try { if (v) sessionStorage.setItem(key, v); else sessionStorage.removeItem(key) } catch { /* private mode */ } }

export function useStoredText(key: string, initial = '') {
  const [text, setText] = useState(() => read(key) ?? initial)
  useEffect(() => write(key, text), [key, text])
  return [text, setText] as const
}
export const clearStoredText = (key: string) => write(key, '')
export const wordCount = (s: string) => (s.trim() ? s.trim().split(/\s+/).length : 0)
