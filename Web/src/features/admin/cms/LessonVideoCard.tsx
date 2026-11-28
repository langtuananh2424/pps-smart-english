import { useState, type ReactNode } from 'react'
import { Section, WriteError } from '../ui'
import { cmsWrites, useCmsWrite, type LessonView } from './api'
import { NodeForm, type FieldDef } from './NodeForm'
import { uploadVideo } from './questions/upload'
import { SubtitleRows } from './SubtitleRows'
import { writeMessage } from './ui'

const videoFields: FieldDef[] = [{ name: 'videoRef', label: 'Video (mã hoặc URL YouTube)' }]
const LESSON_VIDEO_PERMIT = '/admin/cms/media/lesson-video'
type Source = 'YOUTUBE' | 'STORAGE'

// D-171: video bài giảng là YouTube hoặc tệp mp4 tải lên (tối đa theo permit).
function UploadPicker({ lesson }: { lesson: LessonView }) {
  const patch = useCmsWrite((b: object) => cmsWrites.patch('lessons', lesson.id, b))
  const [pct, setPct] = useState<number | null>(null)
  const [err, setErr] = useState('')
  // Cây CMS có thể chưa kèm videoUrl; PATCH trả URL xem thử nên giữ lại.
  const [preview, setPreview] = useState<string | null>(null)
  const url = lesson.videoUrl ?? preview
  const pick = async (f?: File) => {
    if (!f) return
    setErr('')
    setPct(0)
    try {
      const v = await uploadVideo(f, setPct, LESSON_VIDEO_PERMIT)
      const saved = (await patch.mutateAsync({ videoSource: 'STORAGE', videoMediaId: v.id })) as { videoUrl?: string | null } | undefined
      setPreview(saved?.videoUrl ?? null)
    } catch (e) {
      setErr(writeMessage(e))
    } finally {
      setPct(null)
    }
  }
  return (
    <div className="flex flex-col gap-2 rounded-md border border-line-brand bg-page p-3">
      {lesson.videoSource === 'STORAGE' && (
        <p className="flex flex-wrap items-center gap-2 font-bold text-ink">
          Video đã tải lên
          {url && (
            <a href={url} target="_blank" rel="noopener noreferrer" className="font-extrabold text-brand-strong underline">
              Xem thử
            </a>
          )}
        </p>
      )}
      <label htmlFor="lesson-video-file" className="text-sm font-extrabold text-ink">
        Chọn tệp video (mp4)
      </label>
      <input id="lesson-video-file" type="file" accept="video/mp4" disabled={pct !== null} className="min-w-0 max-w-full text-sm font-semibold" onChange={(e) => {
        const f = e.target.files?.[0]
        e.target.value = ''
        void pick(f)
      }} />
      {pct !== null && (
        <div className="flex items-center gap-2">
          <progress value={pct} max={100} aria-label="Tiến trình tải video" className="h-2 flex-1" />
          <span className="text-sm font-bold text-ink-secondary">{pct}%</span>
        </div>
      )}
      {err && (
        <p role="alert" className="font-bold text-danger-fg">
          {err}
        </p>
      )}
      {patch.isError && !err && <WriteError error={patch.error} onRetry={() => patch.reset()} />}
    </div>
  )
}

export function LessonVideoCard({ lesson, children }: { lesson: LessonView; children: ReactNode }) {
  const [source, setSource] = useState<Source>(lesson.videoSource === 'STORAGE' ? 'STORAGE' : 'YOUTUBE')
  const patch = useCmsWrite((b: object) => cmsWrites.patch('lessons', lesson.id, b))
  return (
    <Section title="Video và phụ đề">
      <fieldset className="flex flex-wrap gap-4">
        <legend className="sr-only">Nguồn video</legend>
        {(['YOUTUBE', 'STORAGE'] as const).map((s) => (
          <label key={s} className="flex min-h-11 items-center gap-2 font-bold text-ink">
            <input type="radio" name="video-source" checked={source === s} onChange={() => setSource(s)} className="size-5" />
            {s === 'YOUTUBE' ? 'YouTube' : 'Tải video lên'}
          </label>
        ))}
      </fieldset>
      {source === 'YOUTUBE' ? (
        <NodeForm
          key={`v${lesson.id}`}
          fields={videoFields}
          initial={{ videoRef: lesson.videoSource === 'STORAGE' ? '' : (lesson.videoRef ?? '') }}
          submitLabel="Lưu video"
          // Đổi từ video tải lên sang YouTube phải nói rõ nguồn.
          onSubmit={(b) => patch.mutateAsync(lesson.videoSource === 'STORAGE' ? { ...b, videoSource: 'YOUTUBE' } : b)}
        />
      ) : (
        <UploadPicker lesson={lesson} />
      )}
      <SubtitleRows lesson={lesson} />
      {children}
    </Section>
  )
}
