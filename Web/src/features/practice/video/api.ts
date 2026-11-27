import { api, postJson } from '../../../lib/api'
import type { VideoGroupState, VideoQuizState, WatchProgress } from './types'

export const fetchVideoQuiz = (attemptId: number) => api<VideoQuizState>(`/attempts/${attemptId}/video-quiz`)
// Thời lượng do máy chủ giữ nên không gửi lên; chỉ báo vị trí.
export const reportVideo = (attemptId: number, groupId: number, position: number) =>
  postJson<WatchProgress>(`/attempts/${attemptId}/video-progress`, { groupId, position })
export const submitVideoQuiz = (attemptId: number, groupId: number, answers: { questionId: number; answer: unknown }[]) =>
  postJson<VideoGroupState>(`/attempts/${attemptId}/groups/${groupId}/video-quiz`, { answers })
