import { api, ApiError, postJson, sendKeepalive } from '../../lib/api'
import type { AiSession, Answer, Answers, AnswerItem, AttemptResult, AttemptSummary, Bank, CheckState, PlayGrant, PlayState, Progress, ScriptView, Started } from './types'

export const fetchBank = (id: number) => api<Bank>(`/banks/${id}`)
export const fetchOpenAttempt = async (bankId: number) =>
  (await api<{ data: AttemptSummary[] }>(`/me/attempts?bankId=${bankId}&open=true&limit=1`)).data[0] ?? null
export const startAttempt = (bankId: number) => postJson<Started>(`/banks/${bankId}/attempts`, {})
export const fetchProgress = (id: number) => api<Progress>(`/attempts/${id}/progress`)
export const pauseAttempt = (id: number) => api<Progress>(`/attempts/${id}/pause`, { method: 'POST' })
export const resumeAttempt = (id: number) => api<Progress>(`/attempts/${id}/resume`, { method: 'POST' })
export const checkQuestion = (attemptId: number, questionId: number, answer: Answer) =>
  postJson<CheckState>(`/attempts/${attemptId}/questions/${questionId}/check`, { answer })
export const fetchAiSessions = async (attemptId: number) => (await api<{ data: AiSession[] }>(`/attempts/${attemptId}/ai-sessions`)).data
export const fetchAttempt = (id: number) => api<AttemptResult>(`/attempts/${id}`)
export const submitAttempt = (id: number, answers: Answers) => postJson<AttemptResult>(`/attempts/${id}/submit`, { answers: toItems(answers) })
export const saveDraft = (id: number, answers: Answers) => api<Progress>(`/attempts/${id}/draft`, { method: 'PUT', body: JSON.stringify({ answers: toItems(answers) }) })
// Gửi khi đóng tab: keepalive để trình duyệt hoàn tất yêu cầu sau khi trang đã bỏ.
export const saveDraftOnLeave = (id: number, answers: Answers) =>
  void sendKeepalive(`/attempts/${id}/draft`, { method: 'PUT', body: JSON.stringify({ answers: toItems(answers) }) }).catch(() => {})

// Nghe audio (FR-EX-10): máy chủ đếm lượt; 403 play_limit_reached khi hết lượt.
export const playGroup = (attemptId: number, groupId: number) => api<PlayGrant>(`/attempts/${attemptId}/groups/${groupId}/play`, { method: 'POST' })
export const completePlay = (attemptId: number, groupId: number) => api<PlayState>(`/attempts/${attemptId}/groups/${groupId}/play/complete`, { method: 'POST' })
export const fetchScript = (attemptId: number, questionId: number) => api<ScriptView>(`/attempts/${attemptId}/scripts/${questionId}`)

export const toItems = (answers: Answers): AnswerItem[] =>
  Object.entries(answers).flatMap(([id, answer]) => (answer ? [{ questionId: Number(id), answer }] : []))
export const fromItems = (items: AnswerItem[] | undefined): Answers =>
  Object.fromEntries((items ?? []).map((i) => [i.questionId, i.answer]))

// Nộp bài; lượt đã được nộp (hết giờ hoặc tab khác) thì lấy kết quả có sẵn.
export async function submitOrFetch(id: number, answers: Answers): Promise<AttemptResult> {
  try {
    return await submitAttempt(id, answers)
  } catch (e) {
    if (e instanceof ApiError && e.code === 'attempt_already_submitted') return fetchAttempt(id)
    throw e
  }
}
