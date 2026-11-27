import { useCallback, useEffect, useRef, useState } from 'react'
import { useAiRefs } from '../practice/ai/useAiRefs'

// Phiên Viết/Nói của lượt + theo dõi tải lên đang chạy (khóa Nộp bài, hủy khi rời khung).
export function useAiStage(attemptId: number) {
  const refs = useAiRefs(attemptId)
  const uploads = useRef(new Set<AbortController>())
  const [uploading, setUploading] = useState(false)
  const track = useCallback((ctl: AbortController) => {
    uploads.current.add(ctl)
    setUploading(true)
    return () => {
      uploads.current.delete(ctl)
      setUploading(uploads.current.size > 0)
    }
  }, [])
  useEffect(() => () => uploads.current.forEach((c) => c.abort()), [])
  return { ...refs, track, uploading }
}
export type AiStageState = ReturnType<typeof useAiStage>
