import { api, postJson } from '../../lib/api'
import type { AnswerAccepted, Created, JobView, SessionSummary, SessionView, SubmitAccepted } from './types'

// assignmentId (D-130): chỉ phiên của bài giao đó, không bao giờ rơi về phiên tự luyện cùng câu.
export const latestSession = async (questionId: number, assignmentId?: number) =>
  (await api<{ data: SessionSummary[] }>(`/me/writing?questionId=${questionId}&limit=1${assignmentId ? `&assignmentId=${assignmentId}` : ''}`)).data[0] ?? null
export const fetchSession = (id: number) => api<SessionView>(`/writing/sessions/${id}`)
// attemptId (D-145): mở từ trong lượt đang làm của bộ, điểm AI cộng vào lượt.
// assignmentId (D-130): bài giáo viên giao, không trừ lượt AI tuần.
export const createSession = (questionId: number, attemptId?: number, assignmentId?: number) => postJson<Created>('/writing/sessions', { questionId, attemptId, assignmentId })
export const sendAnswer = (id: number, text: string) => postJson<AnswerAccepted>(`/writing/sessions/${id}/answer`, { text })
export const requestDraft = (id: number) => postJson<{ jobId: number }>(`/writing/sessions/${id}/draft`, {})
export const fetchJob = (id: number) => api<JobView>(`/ai/jobs/${id}`)
// Cùng key cho cùng nội dung: bấm lại sau lỗi mạng không trừ lượt hai lần.
export const submitWriting = (id: number, finalText: string, key: string) =>
  api<SubmitAccepted>(`/writing/sessions/${id}/submit`, { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify({ finalText }) })
