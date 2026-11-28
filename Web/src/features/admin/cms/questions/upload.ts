import { api, ApiError, postJson } from '../../../../lib/api'

interface Permit {
  assetId: number
  url: string
  fields: Record<string, string>
  maxBytes: number
}
export interface UploadedImage {
  id: number
  url: string
  width: number
  height: number
}
export interface UploadedVideo {
  id: number
}
export interface UploadedAudio {
  id: number
  durationMs: number | null
}

const fail = (message: string) => new ApiError(400, 'upload_failed', message)

// Presigned POST: các fields đặt trước, field `file` phải là field cuối cùng (yêu cầu của kho lưu trữ).
async function toStorage(kind: 'images' | 'audio', file: File): Promise<Permit> {
  const permit = await postJson<Permit>(`/admin/cms/media/${kind}`, { contentType: file.type })
  if (file.size > permit.maxBytes) throw fail(`Tệp vượt quá ${Math.floor(permit.maxBytes / 1024 / 1024)} MB.`)
  const form = new FormData()
  for (const [k, v] of Object.entries(permit.fields)) form.append(k, v)
  form.append('file', file)
  const res = await fetch(permit.url, { method: 'POST', body: form })
  if (!res.ok) throw fail('Tải tệp lên thất bại, vui lòng thử lại.')
  return permit
}

export async function uploadImage(file: File): Promise<UploadedImage> {
  const p = await toStorage('images', file)
  return api<UploadedImage>(`/admin/cms/media/${p.assetId}/complete`, { method: 'POST' })
}

// Audio dùng complete-audio, không dùng /complete của ảnh.
export async function uploadAudio(file: File): Promise<UploadedAudio> {
  const p = await toStorage('audio', file)
  return api<UploadedAudio>(`/admin/cms/media/${p.assetId}/complete-audio`, { method: 'POST' })
}

// D-156: video lớn nên dùng XHR để báo tiến trình (fetch không có upload progress). Cùng presigned POST như audio.
export async function uploadVideo(file: File, onProgress: (pct: number) => void, permitPath = '/admin/cms/media/video'): Promise<UploadedVideo> {
  const permit = await postJson<Permit>(permitPath, { contentType: file.type })
  if (file.size > permit.maxBytes) throw fail(`Tệp vượt quá ${Math.floor(permit.maxBytes / 1024 / 1024)} MB.`)
  const form = new FormData()
  for (const [k, v] of Object.entries(permit.fields)) form.append(k, v)
  form.append('file', file)
  await new Promise<void>((resolve, reject) => {
    const x = new XMLHttpRequest()
    x.open('POST', permit.url)
    x.upload.onprogress = (e) => e.lengthComputable && onProgress(Math.round((e.loaded / e.total) * 100))
    x.onload = () => (x.status >= 200 && x.status < 300 ? resolve() : reject(fail('Tải video lên thất bại, vui lòng thử lại.')))
    x.onerror = () => reject(fail('Tải video lên thất bại, vui lòng thử lại.'))
    x.send(form)
  })
  return api<UploadedVideo>(`/admin/cms/media/${permit.assetId}/complete-video`, { method: 'POST' })
}
