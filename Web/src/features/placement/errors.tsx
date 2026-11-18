import { ApiError } from '../../lib/api'

// Lỗi thao tác giữa chừng (lưu, nộp): báo trên màn, bài làm vẫn nằm trên máy.
export function inlineMessage(e: unknown): string {
  if (!(e instanceof ApiError)) return 'Mất kết nối, kiểm tra mạng rồi thử lại.'
  if (e.status === 429) return 'Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút.'
  if (e.status === 503) return 'Hệ thống đang bận, vui lòng thử lại sau ít giây.'
  return e.message
}

export type StartFailure = 'unavailable' | 'exhausted' | { text: string }

// Lỗi lúc bắt đầu lượt: hai trường hợp có màn riêng, còn lại là câu thông báo.
export function startFailure(e: unknown): StartFailure {
  if (e instanceof ApiError && e.code === 'placement_unavailable') return 'unavailable'
  if (e instanceof ApiError && e.code === 'placement_retakes_exhausted') return 'exhausted'
  if (e instanceof ApiError && e.status === 429) return { text: 'Bạn đã bắt đầu quá nhiều lần, hãy thử lại sau.' }
  return { text: inlineMessage(e) }
}
