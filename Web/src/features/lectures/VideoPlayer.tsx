import { useEffect, useRef, type RefObject } from 'react'
import type { LessonVideo } from './api'
import { loadYouTubeApi, YT_ENDED, YT_PAUSED, YT_PLAYING, type YtPlayer } from './youtube'

// Điều khiển từ ngoài (popup giải nghĩa dừng và phát tiếp video).
export interface PlayerControl {
  pause(): void
  play(): void
}

export interface PlayerProps {
  video: LessonVideo
  startAt: number
  // Trình phát gắn hàm đọc vị trí hiện tại (giây) vào đây cho bộ báo tiến độ.
  positionRef: RefObject<() => number>
  onPlay: () => void
  onPause: () => void
  onEnded: () => void
  onDuration?: (sec: number) => void
  onMediaError: () => void
  // Thời gian phát hiện tại (giây) cho phụ đề; YouTube hỏi lại mỗi 250ms khi đang phát.
  onTime?: (sec: number) => void
  controlRef?: RefObject<PlayerControl | null>
}

const frame = 'relative aspect-video w-full overflow-hidden rounded-lg bg-ink'

function StoragePlayer({ video, startAt, positionRef, onPlay, onPause, onEnded, onDuration, onMediaError, onTime, controlRef }: PlayerProps) {
  const el = useRef<HTMLVideoElement>(null)
  // Giữ vị trí khi URL ký được làm mới giữa chừng.
  const resumeAt = useRef(startAt)

  useEffect(() => {
    positionRef.current = () => el.current?.currentTime ?? resumeAt.current
  }, [positionRef])

  useEffect(() => {
    if (controlRef) controlRef.current = { pause: () => el.current?.pause(), play: () => void el.current?.play().catch(() => undefined) }
  }, [controlRef])

  const loaded = () => {
    const v = el.current
    if (!v) return
    onDuration?.(v.duration)
    // Vị trí đã lưu gần cuối thì phát lại từ đầu thay vì dừng ngay.
    v.currentTime = resumeAt.current > 0 && resumeAt.current < v.duration - 2 ? resumeAt.current : 0
  }
  const sync = () => {
    if (el.current) {
      resumeAt.current = el.current.currentTime
      onTime?.(resumeAt.current)
    }
  }

  return (
    <div className={frame}>
      <video
        ref={el}
        src={video.url}
        controls
        controlsList="nodownload"
        disablePictureInPicture
        onContextMenu={(e) => e.preventDefault()}
        onLoadedMetadata={loaded}
        onTimeUpdate={sync}
        onPlay={onPlay}
        onPause={() => {
          sync()
          onPause()
        }}
        onEnded={() => {
          sync()
          onPause()
          onEnded()
        }}
        onError={onMediaError}
        className="size-full"
      />
    </div>
  )
}

function YouTubePlayer({ video, startAt, positionRef, onPlay, onPause, onEnded, onDuration, onMediaError, onTime, controlRef }: PlayerProps) {
  const host = useRef<HTMLDivElement>(null)
  const player = useRef<YtPlayer | null>(null)
  const last = useRef(startAt)
  // Callback mới nhất mà không dựng lại player.
  const cb = useRef({ onPlay, onPause, onEnded, onDuration, onMediaError, onTime })
  cb.current = { onPlay, onPause, onEnded, onDuration, onMediaError, onTime }

  useEffect(() => {
    if (controlRef) controlRef.current = { pause: () => player.current?.pauseVideo(), play: () => player.current?.playVideo() }
  }, [controlRef])

  useEffect(() => {
    positionRef.current = () => {
      try {
        last.current = player.current?.getCurrentTime() ?? last.current
      } catch {
        // player đã hủy: dùng vị trí cuối
      }
      return last.current
    }
  }, [positionRef])

  useEffect(() => {
    let dead = false
    let poll = 0
    const tick = () => {
      try {
        if (player.current) cb.current.onTime?.(player.current.getCurrentTime())
      } catch {
        // player đã hủy
      }
    }
    // YT.Player thay phần tử nó nhận bằng iframe nên tạo một node riêng để React không mất tham chiếu.
    const mount = document.createElement('div')
    host.current?.append(mount)
    loadYouTubeApi()
      .then((YT) => {
        if (dead) return
        player.current = new YT.Player(mount, {
          host: 'https://www.youtube-nocookie.com',
          videoId: video.ref ?? '',
          playerVars: { start: Math.floor(startAt), rel: 0, playsinline: 1, modestbranding: 1 },
          events: {
            onStateChange: (e) => {
              const p = player.current
              if (p) {
                last.current = p.getCurrentTime()
                cb.current.onDuration?.(p.getDuration())
              }
              // YouTube không có timeupdate: hỏi lại khi đang phát, và một lần khi đổi trạng thái (tua lúc dừng).
              window.clearInterval(poll)
              tick()
              if (e.data === YT_PLAYING) poll = window.setInterval(tick, 250)
              if (e.data === YT_PLAYING) cb.current.onPlay()
              else if (e.data === YT_PAUSED) cb.current.onPause()
              else if (e.data === YT_ENDED) {
                cb.current.onPause()
                cb.current.onEnded()
              }
            },
            onError: () => cb.current.onMediaError(),
          },
        })
      })
      .catch(() => cb.current.onMediaError())
    return () => {
      dead = true
      window.clearInterval(poll)
      try {
        player.current?.destroy()
      } catch {
        // iframe đã bị gỡ
      }
      player.current = null
      mount.remove()
    }
    // startAt chỉ dùng khi dựng; đổi video là dựng lại.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [video.ref])

  return <div ref={host} className={`${frame} [&_iframe]:absolute [&_iframe]:inset-0 [&_iframe]:size-full`} />
}

export function VideoPlayer({ failed, video, ...rest }: Omit<PlayerProps, 'video'> & { video: LessonVideo | null; failed: boolean }) {
  const props = { ...rest, video: video as LessonVideo }
  if (!video)
    return (
      <div className={`${frame} grid place-items-center`}>
        <p className="px-6 text-center font-extrabold text-white">Bài giảng chưa có video.</p>
      </div>
    )
  if (!failed && video.source === 'YOUTUBE' && video.ref) return <YouTubePlayer {...props} />
  if (!failed && video.source === 'STORAGE' && video.url) return <StoragePlayer {...props} />
  return (
    <div className={`${frame} grid place-items-center`}>
      <p role="alert" className="px-6 text-center font-extrabold text-white">
        Không phát được video. Hãy tải lại trang.
      </p>
    </div>
  )
}
