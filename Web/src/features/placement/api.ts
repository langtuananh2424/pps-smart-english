import { api, ApiError, postJson } from '../../lib/api'
import { toItems } from '../practice/api'
import type { Answers } from '../practice/types'
import type { PlacementResult, Started, Submitted } from './types'

// Khách xác thực lượt bằng header này (token chỉ nhận một lần lúc bắt đầu); học viên dùng Bearer.
const withToken = (token?: string): RequestInit => ({ headers: token ? { 'X-Placement-Token': token } : {} })

export const startPlacement = () => postJson<Started>('/placement/attempts', {})
export const fetchMine = async () => (await api<{ result: PlacementResult | null }>('/me/placement')).result

export const saveDraft = (id: number, answers: Answers, token?: string) =>
  api<{ remainingSec: number }>(`/placement/attempts/${id}/draft`, {
    ...withToken(token),
    method: 'PUT',
    body: JSON.stringify({ answers: toItems(answers) }),
  })

export const submitPlacement = (id: number, answers: Answers, token?: string) =>
  api<Submitted>(`/placement/attempts/${id}/submit`, {
    ...withToken(token),
    method: 'POST',
    body: JSON.stringify({ answers: toItems(answers) }),
  })

export const claimPlacement = (id: number, token?: string) =>
  api<PlacementResult>(`/placement/attempts/${id}/claim`, { ...withToken(token), method: 'POST' })

// Lỗi mạng (fetch ném TypeError) khác lỗi do server trả: chỉ lỗi mạng mới chờ có mạng rồi gửi lại.
export const isNetworkError = (e: unknown) => !(e instanceof ApiError)
