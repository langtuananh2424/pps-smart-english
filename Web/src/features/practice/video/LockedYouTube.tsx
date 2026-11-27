import { useEffect, useRef, useState, type RefObject } from 'react'
import { loadYouTubeApi, YT_ENDED, YT_PAUSED, YT_PLAYING, type YtPlayer } from '../../lectures/youtube'

// D-158: Video phản xạ điều khiển player này theo mốc câu: phát một đoạn rồi tự dừng, cho dừng/phát lại đoạn đã xem.
export interface ReflexApi {
  play(from: number, to: number | null): void
  halt(): void
}

interface Props {
  videoId: string
  startAt: number
  positionRef: RefObject<() => number>
  onPlay: () => void
  onPause: () => void
  onEnded: () => void
  onMediaError: () => void
  reflex?: { apiRef: RefObject<ReflexApi | null>; onMark: () => void }
}

const JUMP_SEC = 1.5
const POLL_MS = 500
const REFLEX_POLL_MS = 200
const SEEK_SETTLE_MS = 800

// Lần xem đầu của video làm đề (D-159): ẩn thanh điều khiển của YouTube, lớp phủ chặn bấm vào khung, dừng thì phát lại,
// nhảy vị trí vượt chỗ đã xem xa nhất thì seekTo về. Dùng chung IFrame API của bài giảng nhưng không đổi player bài giảng.
export function LockedYouTube({ videoId, startAt, positionRef, onPlay, onPause, onEnded, onMediaError, reflex }: Props) {
  const host = useRef<HTMLDivElement>(null)
  const player = useRef<YtPlayer | null>(null)
  const furthest = useRef(startAt)
  const ended = useRef(false)
  const stopAt = useRef<number | null>(null) // reflex: mốc tự dừng
  const halted = useRef(false) // reflex: đang dừng có chủ đích nên không bật phát lại
  const startedAt = useRef(0) // reflex: seekTo chưa kịp áp dụng thì vị trí còn là mốc cũ, chờ một nhịp rồi mới kiểm mốc dừng
  const [playing, setPlaying] = useState(false)
  const cb = useRef({ onPlay, onPause, onEnded, onMediaError, onMark: reflex?.onMark })
  cb.current = { onPlay, onPause, onEnded, onMediaError, onMark: reflex?.onMark }

  useEffect(() => {
    const ref = reflex?.apiRef
    if (!ref) return
    ref.current = {
      play: (from, to) => {
        halted.current = false
        startedAt.current = Date.now()
        stopAt.current = to
        furthest.current = Math.max(furthest.current, to ?? from) // đoạn đã mở thì không bị kéo lùi
        player.current?.seekTo(from, true)
        player.current?.playVideo()
      },
      halt: () => {
        halted.current = true
        stopAt.current = null
        player.current?.pauseVideo()
      },
    }
    return () => { ref.current = null }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    positionRef.current = () => {
      try {
        return player.current?.getCurrentTime() ?? furthest.current
      } catch {
        return furthest.current
      }
    }
  }, [positionRef])

  useEffect(() => {
    let dead = false
    const mount = document.createElement('div')
    host.current?.append(mount)
    let timer: ReturnType<typeof setInterval> | null = null
    loadYouTubeApi()
      .then((YT) => {
        if (dead) return
        player.current = new YT.Player(mount, {
          host: 'https://www.youtube-nocookie.com',
          videoId,
          playerVars: { start: Math.floor(startAt), rel: 0, playsinline: 1, modestbranding: 1, controls: 0, disablekb: 1, fs: 0 },
          events: {
            onStateChange: (e) => {
              const p = player.current
              if (!p) return
              if (e.data === YT_PLAYING) {
                setPlaying(true)
                cb.current.onPlay()
              } else if (e.data === YT_PAUSED) {
                setPlaying(false)
                cb.current.onPause()
                if (!ended.current && !halted.current) p.playVideo() // lần xem đầu không được dừng
              } else if (e.data === YT_ENDED) {
                ended.current = true
                setPlaying(false)
                cb.current.onEnded()
              }
            },
            onError: () => cb.current.onMediaError(),
          },
        })
        timer = setInterval(() => {
          try {
            const pos = player.current?.getCurrentTime() ?? 0
            if (stopAt.current != null && Date.now() - startedAt.current > SEEK_SETTLE_MS && pos >= stopAt.current - 0.2) {
              stopAt.current = null
              halted.current = true
              player.current?.pauseVideo()
              cb.current.onMark?.()
            } else if (pos > furthest.current + JUMP_SEC) player.current?.seekTo(furthest.current, true)
            else furthest.current = Math.max(furthest.current, pos)
          } catch {
            // player chưa sẵn sàng
          }
        }, reflex ? REFLEX_POLL_MS : POLL_MS)
      })
      .catch(() => cb.current.onMediaError())
    return () => {
      dead = true
      if (timer) clearInterval(timer)
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
  }, [videoId])

  return (
    <div className="relative aspect-video w-full overflow-hidden rounded-lg bg-ink">
      <div ref={host} className="size-full [&_iframe]:absolute [&_iframe]:inset-0 [&_iframe]:size-full" />
      <div className="absolute inset-0 grid place-items-center" data-testid="video-shield">
        {!playing && !ended.current && !halted.current && (
          <button type="button" aria-label="Phát video" onClick={() => player.current?.playVideo()} className="grid size-16 place-items-center rounded-full bg-brand text-2xl font-black text-white">
            ▶
          </button>
        )}
      </div>
    </div>
  )
}
