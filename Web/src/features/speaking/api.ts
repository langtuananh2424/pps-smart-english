import { api, postJson } from '../../lib/api'
import type { Completed, SpeakingQuestion, SubmissionView, UploadPermit } from './types'

export const fetchQuestion = (id: number, assignmentId?: number) =>
  api<SpeakingQuestion>(`/speaking/questions/${id}${assignmentId ? `?assignmentId=${assignmentId}` : ''}`)
// assignmentId (D-130): chỉ lượt của bài giao đó, không bao giờ rơi về lượt tự luyện cùng câu.
export const latestSubmission = async (questionId: number, assignmentId?: number) =>
  (await api<{ data: SubmissionView[] }>(`/me/speaking?questionId=${questionId}&limit=1${assignmentId ? `&assignmentId=${assignmentId}` : ''}`)).data[0] ?? null
export const fetchSubmission = (id: number) => api<SubmissionView>(`/speaking/submissions/${id}`)
export const createSubmission = (questionId: number, attemptId?: number, assignmentId?: number) =>
  postJson<UploadPermit>('/speaking/submissions', { questionId, attemptId, assignmentId })
export const completeSubmission = (id: number) => postJson<Completed>(`/speaking/submissions/${id}/complete`, {})

// Lỗi tải lên MinIO: status 0 là lỗi mạng hoặc CORS (trình duyệt không cho biết nguyên nhân).
export class UploadError extends Error {
  constructor(readonly status: number) {
    super('upload_failed')
  }
}
const UPLOAD_TIMEOUT_MS = 90_000

// Presigned POST: gửi đủ fields theo thứ tự, file đặt cuối. Không qua api(): MinIO không nhận Bearer của app.
function uploadForm(permit: UploadPermit, wav: Blob): FormData {
  const form = new FormData()
  for (const [k, v] of Object.entries(permit.fields)) form.append(k, v)
  form.append('file', wav, 'answer.wav')
  return form
}

export async function uploadAudio(permit: UploadPermit, wav: Blob): Promise<void> {
  const form = uploadForm(permit, wav)
  let res: Response
  try {
    res = await fetch(permit.url, { method: 'POST', body: form, signal: AbortSignal.timeout(UPLOAD_TIMEOUT_MS) })
  } catch {
    throw new UploadError(0)
  }
  if (!res.ok) throw new UploadError(res.status)
}

// Như uploadAudio nhưng báo tiến độ (%) và hủy được bằng signal; hủy thì reject AbortError. fetch chưa có tiến độ tải lên nên dùng XHR.
export function uploadAudioWithProgress(permit: UploadPermit, wav: Blob, onProgress: (percent: number) => void, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const aborted = () => new DOMException('upload aborted', 'AbortError')
    if (signal.aborted) return reject(aborted())
    const xhr = new XMLHttpRequest()
    xhr.open('POST', permit.url)
    xhr.timeout = UPLOAD_TIMEOUT_MS
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(Math.round((e.loaded * 100) / e.total))
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new UploadError(xhr.status)))
    xhr.onerror = xhr.ontimeout = () => reject(new UploadError(0))
    xhr.onabort = () => reject(aborted())
    signal.addEventListener('abort', () => xhr.abort(), { once: true })
    xhr.send(uploadForm(permit, wav))
  })
}
