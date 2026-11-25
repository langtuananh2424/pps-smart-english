import { api, postJson } from '../../lib/api'
import type { AnswerAccepted, Created, JobView, SessionSummary, SessionView, SubmitAccepted } from './types'

export const latestSession = async (questionId: number) =>
  (await api<{ data: SessionSummary[] }>(`/me/writing?questionId=${questionId}&limit=1`)).data[0] ?? null
export const fetchSession = (id: number) => api<SessionView>(`/writing/sessions/${id}`)
// attemptId (D-145): mở từ trong lượt đang làm của bộ, điểm AI cộng vào lượt.
export const createSession = (questionId: number, attemptId?: number) => postJson<Created>('/writing/sessions', { questionId, attemptId })
export const sendAnswer = (id: number, text: string) => postJson<AnswerAccepted>(`/writing/sessions/${id}/answer`, { text })
export const requestDraft = (id: number) => postJson<{ jobId: number }>(`/writing/sessions/${id}/draft`, {})
export const fetchJob = (id: number) => api<JobView>(`/ai/jobs/${id}`)
// Cùng key cho cùng nội dung: bấm lại sau lỗi mạng không trừ lượt hai lần.
export const submitWriting = (id: number, finalText: string, key: string) =>
  api<SubmitAccepted>(`/writing/sessions/${id}/submit`, { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify({ finalText }) })
