import { useId } from 'react'
import { ApiError } from '../../../lib/api'
import { labelCls, WriteError } from '../ui'
import { cmsWrites, useCmsWrite, type LessonView, type SubtitleLang } from './api'
import { DeleteButton } from './DeleteButton'

const LANGS: { lang: SubtitleLang; label: string }[] = [
  { lang: 'en', label: 'Tiếng Anh' },
  { lang: 'vi', label: 'Tiếng Việt' },
]

// 400 của máy chủ nằm ở field "file" (rỗng, sai định dạng, quá lớn...).
const fileError = (e: unknown) => (e instanceof ApiError && e.status === 400 ? ((e.details as { file?: string } | undefined)?.file ?? e.message) : null)

function Row({ lessonId, lang, label, cueCount }: { lessonId: number; lang: SubtitleLang; label: string; cueCount?: number }) {
  const id = useId()
  const put = useCmsWrite((f: File) => {
    const form = new FormData()
    form.append('file', f)
    return cmsWrites.putSubtitle(lessonId, lang, form)
  })
  const del = useCmsWrite(() => cmsWrites.deleteSubtitle(lessonId, lang))
  const failed = put.isError ? put : del.isError ? del : null
  const msg = failed && fileError(failed.error)
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-bold text-ink">
        {label}
      </label>
      <div className="flex flex-wrap items-center gap-2">
        <input
          id={id}
          type="file"
          accept=".vtt,.srt"
          aria-invalid={!!msg}
          disabled={put.isPending}
          className="min-w-0 max-w-full text-sm font-semibold"
          onChange={(e) => {
            const f = e.target.files?.[0]
            e.target.value = '' // cho phép chọn lại cùng tệp
            if (f) put.mutate(f)
          }}
        />
        {cueCount != null && <span className="text-sm font-semibold text-ink-secondary">{cueCount} câu phụ đề</span>}
        {cueCount != null && <DeleteButton label={`phụ đề ${label}`} pending={del.isPending} onConfirm={() => del.mutate(undefined)} />}
      </div>
      {msg && (
        <p role="alert" className="text-sm font-bold text-danger-fg">
          {msg}
        </p>
      )}
      {failed && !msg && <WriteError error={failed.error} onRetry={() => failed.reset()} />}
    </div>
  )
}

// FR-LMS-02, D-172: phụ đề VTT/SRT cho từng ngôn ngữ.
export function SubtitleRows({ lesson }: { lesson: LessonView }) {
  return (
    <fieldset className="flex flex-col gap-3 rounded-md border border-line-brand bg-page p-3">
      <legend className={labelCls}>Phụ đề VTT hoặc SRT</legend>
      {LANGS.map((l) => (
        <Row key={l.lang} lessonId={lesson.id} {...l} cueCount={lesson.subtitles?.find((s) => s.lang === l.lang)?.cueCount} />
      ))}
    </fieldset>
  )
}
