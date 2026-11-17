import { api, postJson } from '../../lib/api'
import type { Completed, SpeakingQuestion, SubmissionView, UploadPermit } from './types'

export const fetchQuestion = (id: number) => api<SpeakingQuestion>(`/speaking/questions/${id}`)
export const latestSubmission = async (questionId: number) =>
  (await api<{ data: SubmissionView[] }>(`/me/speaking?questionId=${questionId}&limit=1`)).data[0] ?? null
export const fetchSubmission = (id: number) => api<SubmissionView>(`/speaking/submissions/${id}`)
export const createSubmission = (questionId: number) => postJson<UploadPermit>('/speaking/submissions', { questionId })
export const completeSubmission = (id: number) => postJson<Completed>(`/speaking/submissions/${id}/complete`, {})

// Lỗi tải lên MinIO: status 0 là lỗi mạng hoặc CORS (trình duyệt không cho biết nguyên nhân).
export class UploadError extends Error {
  constructor(readonly status: number) {
    super('upload_failed')
  }
}
const UPLOAD_TIMEOUT_MS = 90_000

// Presigned POST: gửi đủ fields theo thứ tự, file đặt cuối. Không qua api(): MinIO không nhận Bearer của app.
export async function uploadAudio(permit: UploadPermit, wav: Blob): Promise<void> {
  const form = new FormData()
  for (const [k, v] of Object.entries(permit.fields)) form.append(k, v)
  form.append('file', wav, 'answer.wav')
  let res: Response
  try {
    res = await fetch(permit.url, { method: 'POST', body: form, signal: AbortSignal.timeout(UPLOAD_TIMEOUT_MS) })
  } catch {
    throw new UploadError(0)
  }
  if (!res.ok) throw new UploadError(res.status)
}
