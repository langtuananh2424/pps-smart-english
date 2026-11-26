import { api, postJson } from '../../lib/api'
import type { Started } from '../practice/types'
import type { IeltsMode, IeltsResult, QuestionHints } from './types'

// D-151: chế độ chọn lúc bắt đầu lượt.
export const startIeltsAttempt = (bankId: number, mode: IeltsMode) => postJson<Started>(`/banks/${bankId}/attempts`, { mode })
export const fetchHints = (attemptId: number) => api<QuestionHints[]>(`/attempts/${attemptId}/hints`)
export const fetchIeltsResult = (attemptId: number) => api<IeltsResult>(`/attempts/${attemptId}`)
