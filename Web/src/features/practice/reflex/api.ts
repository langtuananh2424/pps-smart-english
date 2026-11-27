import { api } from '../../../lib/api'
import type { ReflexProgress } from '../types'

// D-158: tiến độ bước Viết/Nói của một câu Video phản xạ; máy chủ quyết định bước Nói đã mở chưa.
export const fetchReflex = (attemptId: number, questionId: number) => api<ReflexProgress>(`/attempts/${attemptId}/video-reflex/${questionId}`)
