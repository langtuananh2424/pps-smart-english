import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../lib/api'
import { completePlay, playGroup } from '../api'
import { pillPrimary } from '../questions/ui'
import type { BankGroup, PlaybackMode } from '../types'

interface Props {
  attemptId: number
  group: BankGroup
  mode: PlaybackMode
  defaultLimit?: number | null
  disabled?: boolean
}

const OUT = 'Đã hết lượt nghe'

// Thẻ audio của một nhóm (D-135). FREE: tự do phát/tua; EXAM/LOCKED: không tua, máy chủ đếm lượt.
export function GroupAudio({ attemptId, group, mode, defaultLimit, disabled }: Props) {
  const free = mode === 'FREE'
  const audio = useRef<HTMLAudioElement | null>(null)
  const started = useRef(false)
  const alive = useRef(true)
  const [playing, setPlaying] = useState(false)
  const [played, setPlayed] = useState(false) // đã phát ít nhất một lần
  const [ended, setEnded] = useState(false)
  const [remaining, setRemaining] = useState<number | undefined>()
  const [blocked, setBlocked] = useState(false) // trình duyệt chặn autoplay
  const [busy, setBusy] = useState(false)
  const [note, setNote] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [time, setTime] = useState(0)
  const [dur, setDur] = useState((group.audioDurationMs ?? 0) / 1000)

  const el = () => (audio.current ??= new Audio())

  async function onEnded() {
    setPlaying(false)
    setEnded(true)
    if (free) return // FREE không có lượt: gọi complete sẽ bị 409 play_not_started
    try {
      const s = await completePlay(attemptId, group.id)
      if (s.remaining != null) setRemaining(s.remaining)
    } catch (e) {
      fail(e)
    }
  }
  const endedRef = useRef(onEnded)
  endedRef.current = onEnded

  useEffect(() => {
    alive.current = true
    const a = el()
    const on = (n: string, f: () => void) => a.addEventListener(n, f)
    on('timeupdate', () => setTime(a.currentTime))
    on('loadedmetadata', () => Number.isFinite(a.duration) && setDur(a.duration))
    on('ended', () => void endedRef.current())
    on('pause', () => setPlaying(false))
    on('play', () => setPlaying(true))
    // Autoplay chỉ chạy một lần; Strict Mode dựng lại thì chỉ nối tiếp nếu đã có nguồn.
    if (mode === 'EXAM' && !disabled) {
      if (!started.current) void start()
      else if (a.src && a.paused && !a.ended) void a.play().catch(() => {})
    }
    return () => {
      alive.current = false
      a.pause()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function fail(e: unknown) {
    if (e instanceof ApiError && e.code === 'play_limit_reached') {
      setRemaining(0)
      setEnded(true)
      setNote(OUT)
    } else if (e instanceof ApiError && e.status === 409) setNote('Đang phát, vui lòng đợi.')
    else setError(e instanceof ApiError ? e.message : 'Không phát được audio, thử lại.')
  }

  // Mỗi lần phát lấy url mới (presigned ngắn hạn), không giữ lại.
  async function start() {
    started.current = true
    setBusy(true)
    setError(null)
    setNote(null)
    try {
      const g = await playGroup(attemptId, group.id)
      if (g.remaining != null) setRemaining(g.remaining)
      if (!alive.current) return
      const a = el()
      a.src = g.url
      setEnded(false)
      setPlayed(true)
      setTime(0)
      await a.play().catch((e) => {
        if (e?.name !== 'NotAllowedError') throw e
        setBlocked(true) // lượt đã tính, chờ người dùng bấm "Bắt đầu nghe"
      })
    } catch (e) {
      fail(e)
    } finally {
      setBusy(false)
    }
  }

  const begin = () => {
    setBlocked(false)
    void el().play()
  }
  const toggle = () => {
    const a = el()
    if (free && played && !ended) return playing ? a.pause() : void a.play()
    void start()
  }

  const exhausted = !free && ended && remaining === 0
  const canAgain = ended && (free || (remaining ?? 0) > 0)
  const label = blocked ? 'Bắt đầu nghe' : playing ? 'Tạm dừng' : ended ? 'Nghe lại' : free && played ? 'Tiếp tục' : 'Phát'
  // LOCKED/EXAM: chỉ nghe lại khi audio đã hết; đang phát thì khóa nút.
  const lock = disabled || busy || exhausted || (!free && (playing || (played && !ended)))
  const pct = dur > 0 ? Math.min(100, (time / dur) * 100) : 0
  const status = exhausted ? OUT : playing ? 'Đang phát…' : blocked ? 'Trình duyệt chặn tự phát, bấm để nghe.' : ended ? 'Đã phát xong.' : 'Sẵn sàng nghe.'

  return (
    <section aria-label="Audio" className="flex flex-col gap-2.5 rounded-lg border-[1.5px] border-line bg-surface p-4">
      <div className="flex items-center gap-3">
        <button
          type="button"
          aria-label={label}
          disabled={lock}
          onClick={blocked ? begin : toggle}
          className={`${pillPrimary} !h-11 shrink-0 !px-4 !text-base`}
        >
          {playing ? '❚❚' : '▶'} {label}
        </button>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-extrabold text-ink">{group.title ?? 'Nghe audio'}</p>
          <p className="text-[13px] font-semibold text-ink-secondary">{status}</p>
        </div>
        {!free && (
          <p className="shrink-0 text-sm font-extrabold text-brand-strong">
            {remaining != null ? `Còn ${remaining} lần nghe` : defaultLimit != null ? `Tổng ${defaultLimit} lần nghe` : null}
          </p>
        )}
      </div>
      <div role="progressbar" aria-label="Tiến độ audio" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(pct)} className="h-1.5 overflow-hidden rounded-full bg-muted">
        <div className="h-full bg-[#f76707]" style={{ width: `${pct}%` }} />
      </div>
      {free && (
        <input
          type="range" aria-label="Tua audio" min={0} max={dur || 0} step={0.1} value={Math.min(time, dur || 0)} disabled={disabled || !played}
          onChange={(e) => { el().currentTime = Number(e.target.value) }}
          className="w-full accent-[#f76707]"
        />
      )}
      {note && <p role="status" className="text-sm font-bold text-ink-secondary">{note}</p>}
      {error && <p role="alert" className="rounded-md bg-danger-bg px-3 py-2 text-sm font-bold text-danger">{error}</p>}
    </section>
  )
}
