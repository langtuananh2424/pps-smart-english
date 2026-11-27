import { api, postJson } from '../../lib/api'
import type { Started } from '../practice/types'
import type { IeltsResult, QuestionHints } from './types'

// D-155: chế độ do bộ/bài giao quyết định, client không gửi.
export const startIeltsAttempt = (bankId: number) => postJson<Started>(`/banks/${bankId}/attempts`, {})
export const fetchHints = (attemptId: number) => api<QuestionHints[]>(`/attempts/${attemptId}/hints`)
export const fetchIeltsResult = (attemptId: number) => api<IeltsResult>(`/attempts/${attemptId}`)
