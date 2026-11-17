import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { formatClock } from '../practice/useCountdown'
import { recorderMessage } from './errors'
import { canRecord, type RecorderError } from './useRecorder'

export interface Audio { wav: Blob; url: string; seconds: number }
interface Props {
  maxSeconds: number
  audio: Audio | null
  converting: boolean
  recording: boolean
  elapsed: number
  recorderError: RecorderError | null
  disabled: boolean
  onStart: () => void
  onStop: () => void
  onPick: (file: File) => void
  onReset: () => void
}

const small = '!h-11 !text-base'

// Khung ghi âm (Figma 283:5555 / 294:13422): ghi micro hoặc chọn tệp, nghe lại, ghi lại. Việc đổi sang WAV do màn cha làm.
export function Recorder(p: Props) {
  return (
    <div className="flex flex-col gap-2.5 rounded-[14px] border border-line bg-input p-4">
      <h2 className="text-lg font-black leading-6 text-ink">Ghi âm câu trả lời</h2>
      <p aria-label="Thời gian ghi" className="text-[15px] font-extrabold tabular-nums text-ink-secondary">
        {p.recording && <span aria-hidden="true" className="mr-2 inline-block size-2.5 animate-pulse rounded-full bg-danger" />}
        {formatClock(Math.floor(p.audio ? p.audio.seconds : p.elapsed))} / {formatClock(p.maxSeconds)}
      </p>
      {p.converting ? (
        <p role="status" className="text-base font-extrabold text-brand-ink">Đang xử lý bản ghi…</p>
      ) : p.audio ? (
        <>
          <audio aria-label="Nghe lại bản ghi" controls src={p.audio.url} className="w-full" />
          <div className="flex flex-wrap gap-3">
            <button type="button" disabled={p.disabled} onClick={p.onReset} className={`${pillNeutral} ${small}`}>Ghi lại</button>
          </div>
        </>
      ) : p.recording ? (
        <div className="flex">
          <button type="button" onClick={p.onStop} className={`${pillPrimary} ${small}`}>Dừng ghi</button>
        </div>
      ) : (
        <div className="flex flex-wrap gap-3">
          {canRecord() && <button type="button" onClick={p.onStart} className={`${pillPrimary} ${small}`}>Bắt đầu ghi</button>}
          <label className={`${pillNeutral} ${small} cursor-pointer focus-within:outline focus-within:outline-2 focus-within:outline-brand`}>
            Chọn tệp audio
            <input
              type="file"
              accept="audio/*"
              className="sr-only"
              onChange={(e) => {
                const f = e.target.files?.[0]
                e.target.value = '' // chọn lại cùng tệp vẫn kích hoạt onChange
                if (f) p.onPick(f)
              }}
            />
          </label>
        </div>
      )}
      {p.recorderError && <p role="alert" className="text-sm font-bold text-danger-fg">{recorderMessage(p.recorderError)}</p>}
    </div>
  )
}
