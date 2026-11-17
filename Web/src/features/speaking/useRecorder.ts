import { useCallback, useEffect, useRef, useState } from 'react'

export type RecorderError = 'unsupported' | 'denied'
export const canRecord = () => typeof MediaRecorder !== 'undefined' && !!navigator.mediaDevices?.getUserMedia

// Ghi âm micro bằng MediaRecorder, tự dừng khi tới maxSeconds. Định dạng đầu ra tùy trình duyệt, chuyển WAV ở wav.ts.
export function useRecorder(maxSeconds: number, onRecorded: (blob: Blob) => void) {
  const [recording, setRecording] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const [error, setError] = useState<RecorderError | null>(null)
  const rec = useRef<MediaRecorder | null>(null)
  const timer = useRef<ReturnType<typeof setInterval>>(undefined)
  const done = useRef(onRecorded)
  done.current = onRecorded

  const stop = useCallback(() => {
    clearInterval(timer.current)
    if (rec.current?.state === 'recording') rec.current.stop()
  }, [])

  const start = useCallback(async () => {
    setError(null)
    if (!canRecord()) return setError('unsupported')
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      return setError('denied')
    }
    const chunks: Blob[] = []
    const r = new MediaRecorder(stream)
    rec.current = r
    r.ondataavailable = (e) => e.data.size && chunks.push(e.data)
    r.onstop = () => {
      stream.getTracks().forEach((t) => t.stop()) // tắt đèn micro
      setRecording(false)
      done.current(new Blob(chunks, { type: r.mimeType }))
    }
    r.start()
    const t0 = Date.now()
    setElapsed(0)
    setRecording(true)
    timer.current = setInterval(() => {
      const s = (Date.now() - t0) / 1000
      setElapsed(Math.min(s, maxSeconds))
      if (s >= maxSeconds) stop()
    }, 200)
  }, [maxSeconds, stop])

  // Rời trang khi đang ghi: dừng, nhả micro, bỏ kết quả.
  useEffect(() => () => {
    clearInterval(timer.current)
    const r = rec.current
    if (r && r.state === 'recording') {
      r.onstop = null
      r.stop()
      r.stream.getTracks().forEach((t) => t.stop())
    }
  }, [])

  return { recording, elapsed, error, start, stop }
}
