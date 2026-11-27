import { useEffect, useRef, useState, type RefObject } from 'react'
import { LockedYouTube, type ReflexApi } from '../video/LockedYouTube'
import type { BankGroup } from '../types'

export type { ReflexApi }
const noop = () => {}

interface Props {
  group: BankGroup
  apiRef: RefObject<ReflexApi | null>
  onMark: () => void // video tự dừng ở mốc đã hẹn
  expectPlaying: boolean // phiên đang chờ video chạy: nếu video đứng thì hiện nút Phát (trình duyệt có thể chặn tự phát)
}

// Video của nhóm (D-156) cho Video phản xạ: không thanh điều khiển, phát từng đoạn [from, to] rồi tự dừng ở mốc câu (D-158).
// File mp4 dùng thẻ video; YouTube dùng LockedYouTube (controls: 0, seekTo).
export function ReflexVideo({ group, apiRef, onMark, expectPlaying }: Props) {
  const el = useRef<HTMLVideoElement>(null)
  const stopAt = useRef<number | null>(null)
  const mark = useRef(onMark)
  mark.current = onMark
  const [playing, setPlaying] = useState(false)
  const [failed, setFailed] = useState(false)
  const positionRef = useRef<() => number>(() => 0)

  useEffect(() => {
    if (!group.videoUrl) return
    const ref = apiRef
    ref.current = {
      play: (from, to) => {
        const v = el.current
        if (!v) return
        stopAt.current = to
        v.currentTime = from
        Promise.resolve(v.play()).catch(() => {}) // bị chặn tự phát thì nút Phát bên dưới
      },
      halt: () => {
        stopAt.current = null
        el.current?.pause()
      },
    }
    return () => { ref.current = null }
  }, [group.videoUrl, apiRef])

  if (failed || (!group.videoUrl && !group.videoYoutubeId))
    return <p role="alert" className="grid aspect-video place-items-center rounded-lg bg-ink px-6 text-center font-extrabold text-white">Không phát được video. Hãy tải lại trang.</p>

  if (!group.videoUrl)
    return <LockedYouTube videoId={group.videoYoutubeId!} startAt={0} positionRef={positionRef} onPlay={noop} onPause={noop} onEnded={noop} onMediaError={() => setFailed(true)} reflex={{ apiRef, onMark }} />

  const reached = () => {
    const t = stopAt.current
    if (t == null) return
    stopAt.current = null
    el.current?.pause()
    mark.current()
  }
  return (
    <div className="relative aspect-video w-full overflow-hidden rounded-lg bg-ink">
      <video
        ref={el} aria-label="Video đề" src={group.videoUrl} playsInline disablePictureInPicture
        onContextMenu={(e) => e.preventDefault()} onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} onError={() => setFailed(true)}
        onTimeUpdate={() => { const v = el.current; if (v && stopAt.current != null && v.currentTime >= stopAt.current - 0.05) reached() }}
        onEnded={reached} className="size-full"
      />
      {expectPlaying && !playing && (
        <button type="button" aria-label="Phát video" onClick={() => void el.current?.play()} className="absolute inset-0 m-auto grid size-16 place-items-center rounded-full bg-brand text-2xl font-black text-white">▶</button>
      )}
    </div>
  )
}
