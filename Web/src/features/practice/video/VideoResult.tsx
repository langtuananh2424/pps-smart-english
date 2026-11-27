import { useQuery } from '@tanstack/react-query'
import { fetchVideoQuiz } from './api'

// Màn kết quả lượt: "Lượt N · Đã chốt" và số lượt đã đạt / cần đạt để hoàn thành bộ.
export function VideoResult({ attemptId }: { attemptId: number }) {
  const q = useQuery({ queryKey: ['attempt', attemptId, 'video-quiz'], queryFn: () => fetchVideoQuiz(attemptId), retry: false })
  if (!q.data) return null
  const done = q.data.passedAttempts >= q.data.requiredAttempts
  return (
    <p role="status" className="rounded-lg bg-subtle p-4 text-center text-sm font-bold text-ink-secondary">
      Lượt {q.data.attemptNo} · Đã chốt · Đã đạt {q.data.passedAttempts}/{q.data.requiredAttempts} lượt cần đạt
      {done ? ' · Bộ đã hoàn thành' : ' · Làm lại để đạt thêm'}
    </p>
  )
}
