import { api } from '../../lib/api'
import { clearLocalQa, readLocalQa } from './storage'

// AC US-101 ý 3: sau đăng ký/đăng nhập, Q&A của khách vào hồ sơ nếu server chưa có (không ghi đè).
export async function syncGuestQa() {
  try {
    const local = readLocalQa()
    if (local && (await api<unknown>('/me/qa')) === undefined) {
      await api('/me/qa', { method: 'PUT', body: JSON.stringify(local) })
    }
    clearLocalQa()
  } catch {
    // Lỗi mạng: giữ localStorage để thử lại lần đăng nhập sau, không chặn đăng nhập.
  }
}
