import { qaSchema, type QaAnswers } from './schema'

// Khách lưu câu trả lời ở trình duyệt (D-55), ghi vào hồ sơ sau khi đăng nhập.
const KEY = 'pps.qa'

export function readLocalQa(): QaAnswers | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const p = qaSchema.safeParse(JSON.parse(raw))
    return p.success ? p.data : null
  } catch {
    return null
  }
}

export function writeLocalQa(a: QaAnswers) {
  try {
    localStorage.setItem(KEY, JSON.stringify(a))
  } catch {
    /* localStorage bị chặn: chỉ mất việc nhớ cho khách */
  }
}

export function clearLocalQa() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    /* như trên */
  }
}
