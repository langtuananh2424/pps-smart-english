import { useEffect, useRef, useState, type RefObject } from 'react'
import { VideoPlayer } from '../../lectures/VideoPlayer'
import type { BankGroup } from '../types'
import { reportVideo } from './api'
import { LockedYouTube } from './LockedYouTube'
import type { WatchProgress } from './types'

export const REPORT_MS = 5000

interface Props {
  attemptId: number
  group: BankGroup
  startAt: number
  // Lần xem đầu: khóa tạm dừng và tua tới. Xem đủ rồi thì trình phát tự do.
  locked: boolean
  onProgress: (p: WatchProgress) => void
}

// Báo vị trí xem định kỳ khi đang phát, và ngay khi phát, dừng, hết video. Máy chủ mới là nơi quyết định phần nào được tính.
function useReporter(attemptId: number, groupId: number, positionRef: RefObject<() => number>, onProgress: Props['onProgress']) {
  const timer = useRef<ReturnType<typeof setInterval> | null>(null)
  const cb = useRef(onProgress)
  cb.current = onProgress
  const send = () => {
    reportVideo(attemptId, groupId, positionRef.current()).then((p) => cb.current(p)).catch(() => {})
  }
  const stop = () => {
    if (timer.current) clearInterval(timer.current)
    timer.current = null
  }
  useEffect(() => stop, [])
  return {
    start: () => {
      send()
      stop()
      timer.current = setInterval(send, REPORT_MS)
    },
    end: () => {
      stop()
      send()
    },
  }
}

// File mp4: bỏ thanh điều khiển, không cho dừng hay tua tới vượt chỗ đã xem. YouTube: LockedYouTube khi khóa, player bài giảng khi đã xem đủ.
export function WatchPlayer({ attemptId, group, startAt, locked, onProgress }: Props) {
  const positionRef = useRef<() => number>(() => startAt)
  const report = useReporter(attemptId, group.id, positionRef, onProgress)
  const [failed, setFailed] = useState(false)
  const el = useRef<HTMLVideoElement>(null)
  const furthest = useRef(startAt)

  if (group.videoUrl) {
    const v = () => el.current
    positionRef.current = () => v()?.currentTime ?? 0
    return (
      <video
        ref={el}
        aria-label="Video đề"
        src={group.videoUrl}
        controls={!locked}
        controlsList="nodownload noplaybackrate"
        disablePictureInPicture
        onContextMenu={(e) => e.preventDefault()}
        onLoadedMetadata={() => {
          const x = v()
          if (x && startAt > 0 && startAt < x.duration - 2) x.currentTime = startAt
        }}
        onTimeUpdate={() => {
          const x = v()
          if (x && x.currentTime <= furthest.current + 1.5) furthest.current = Math.max(furthest.current, x.currentTime)
        }}
        onSeeking={() => {
          const x = v()
          if (locked && x && x.currentTime > furthest.current + 1.5) x.currentTime = furthest.current
        }}
        onRateChange={() => {
          const x = v()
          if (locked && x) x.playbackRate = 1
        }}
        onClick={(e) => {
          const x = e.currentTarget
          if (locked && x.paused) void x.play()
        }}
        onPlay={report.start}
        onPause={(e) => {
          report.end()
          const x = e.currentTarget
          if (locked && !x.ended) void x.play().catch(() => {}) // lần xem đầu không được dừng
        }}
        onEnded={report.end}
        onError={() => setFailed(true)}
        className="aspect-video w-full overflow-hidden rounded-lg bg-ink"
      />
    )
  }
  if (failed || !group.videoYoutubeId)
    return <p role="alert" className="grid aspect-video place-items-center rounded-lg bg-ink px-6 text-center font-extrabold text-white">Không phát được video. Hãy tải lại trang.</p>
  if (locked)
    return <LockedYouTube videoId={group.videoYoutubeId} startAt={startAt} positionRef={positionRef} onPlay={report.start} onPause={report.end} onEnded={report.end} onMediaError={() => setFailed(true)} />
  return (
    <VideoPlayer
      failed={false}
      video={{ source: 'YOUTUBE', ref: group.videoYoutubeId }}
      startAt={startAt}
      positionRef={positionRef}
      onPlay={report.start}
      onPause={report.end}
      onEnded={report.end}
      onMediaError={() => setFailed(true)}
    />
  )
}
