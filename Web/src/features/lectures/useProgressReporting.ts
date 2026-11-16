import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, type RefObject } from 'react'
import { lessonProgressKey, postLessonProgress } from './api'
import { ProgressTracker } from './ProgressTracker'

// Khóa cache bị ảnh hưởng khi tiến độ đổi (cờ completed, % khóa, việc hôm nay, XP).
const AFFECTED = [['me', 'roadmap'], ['me', 'courses'], ['me', 'today'], ['me', 'stats']]

// Chỉ báo tiến độ khi đã đăng nhập; khách xem thử không gửi.
export function useProgressReporting(lessonId: number, enabled: boolean, position: RefObject<() => number>) {
  const qc = useQueryClient()
  const tracker = useRef<ProgressTracker | null>(null)

  useEffect(() => {
    if (!enabled) return
    const t = new ProgressTracker(
      (body, final) => {
        postLessonProgress(lessonId, body)
          .then(() => {
            qc.invalidateQueries({ queryKey: lessonProgressKey(lessonId) })
            if (final) AFFECTED.forEach((queryKey) => qc.invalidateQueries({ queryKey }))
          })
          .catch(() => {}) // mất một lần báo không làm hỏng việc xem; lần sau gửi bù vị trí.
      },
      () => position.current(),
    )
    tracker.current = t
    const onHide = () => document.visibilityState === 'hidden' && t.flush(false)
    document.addEventListener('visibilitychange', onHide)
    return () => {
      document.removeEventListener('visibilitychange', onHide)
      t.pause()
      tracker.current = null
    }
  }, [lessonId, enabled, qc, position])

  return { onPlay: () => tracker.current?.play(), onPause: () => tracker.current?.pause() }
}
