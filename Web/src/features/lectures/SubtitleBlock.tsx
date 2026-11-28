import { useEffect, useMemo, useRef, useState, type RefObject } from 'react'
import type { GlossaryEntry, LessonDetail } from './api'
import { GlossaryDialog } from './GlossaryDialog'
import { findCueIndex, loadMode, makeSplitter, saveMode, type SubtitleMode } from './subtitles'
import type { PlayerControl } from './VideoPlayer'

const MODES: { value: SubtitleMode; label: string }[] = [
  { value: 'both', label: 'Song ngữ' },
  { value: 'en', label: 'Tiếng Anh' },
  { value: 'off', label: 'Tắt' },
]

interface Props {
  subtitles: NonNullable<LessonDetail['subtitles']>
  glossary: GlossaryEntry[]
  // Trình phát đẩy thời gian (giây) vào đây; khối này tự đăng ký hàm nhận.
  timeRef: RefObject<(sec: number) => void>
  control: RefObject<PlayerControl | null>
  playingRef: RefObject<boolean>
}

// FR-LMS-02: phụ đề dưới video, cụm trong glossary bấm được để dừng video và xem nghĩa.
export function SubtitleBlock({ subtitles, glossary, timeRef, control, playingRef }: Props) {
  const [mode, setMode] = useState(loadMode)
  // Lưu chỉ số cue thay vì thời gian để chỉ dựng lại khi cue đổi.
  const [at, setAt] = useState('-1|-1')
  const [open, setOpen] = useState<{ entry: GlossaryEntry; resume: boolean } | null>(null)
  const trigger = useRef<HTMLElement | null>(null)
  const split = useMemo(() => makeSplitter(glossary), [glossary])

  useEffect(() => {
    timeRef.current = (sec) => setAt(`${findCueIndex(subtitles.en, sec * 1000)}|${findCueIndex(subtitles.vi, sec * 1000)}`)
  }, [timeRef, subtitles])

  if (subtitles.en.length + subtitles.vi.length === 0) return null
  const [ei, vi] = at.split('|').map(Number)
  const en = subtitles.en[ei]?.text
  const vn = subtitles.vi[vi]?.text

  const show = (entry: GlossaryEntry, el: HTMLElement) => {
    trigger.current = el
    const resume = playingRef.current
    if (resume) control.current?.pause()
    setOpen({ entry, resume })
  }
  const close = () => {
    if (open?.resume) control.current?.play()
    setOpen(null)
    trigger.current?.focus()
  }

  return (
    <section aria-label="Phụ đề" className="flex flex-col gap-2 rounded-lg border border-line-brand bg-surface p-3">
      <div className="flex items-center justify-end gap-2">
        <label htmlFor="subtitle-mode" className="text-sm font-bold text-ink-secondary">
          Phụ đề
        </label>
        <select
          id="subtitle-mode"
          value={mode}
          onChange={(e) => {
            const m = e.target.value as SubtitleMode
            setMode(m)
            saveMode(m)
          }}
          className="h-11 rounded-md border border-line-brand bg-surface px-2 text-sm font-bold text-ink"
        >
          {MODES.map((m) => (
            <option key={m.value} value={m.value}>
              {m.label}
            </option>
          ))}
        </select>
      </div>
      {mode !== 'off' && (
        <div className="flex min-h-[4.5rem] flex-col gap-1">
          <p lang="en" className="text-lg font-extrabold leading-7 text-ink">
            {en &&
              split(en).map((s, i) =>
                s.entry ? (
                  <button key={i} type="button" onClick={(e) => show(s.entry!, e.currentTarget)} className="font-extrabold text-brand-strong underline underline-offset-4">
                    {s.text}
                  </button>
                ) : (
                  <span key={i}>{s.text}</span>
                ),
              )}
          </p>
          {mode === 'both' && (
            <p lang="vi" className="font-semibold leading-6 text-ink-secondary">
              {vn}
            </p>
          )}
        </div>
      )}
      {open && <GlossaryDialog entry={open.entry} onClose={close} />}
    </section>
  )
}
