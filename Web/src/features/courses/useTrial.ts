import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { api } from '../../lib/api'
import type { CourseDetail } from './types'

export interface Trial {
  open: (slug: string) => void
  busy: string | null
  failed: boolean
}

// Bài đầu của topic học thử (D-35/D-57) lấy từ chi tiết khóa; khách phải đăng nhập rồi quay lại bài đó.
export function useTrial(): Trial {
  const nav = useNavigate()
  const qc = useQueryClient()
  const { user } = useAuth()
  const [busy, setBusy] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  const open = async (slug: string) => {
    setBusy(slug)
    setFailed(false)
    try {
      const d = await qc.fetchQuery({ queryKey: ['catalog', 'course', slug], queryFn: () => api<CourseDetail>(`/catalog/courses/${slug}`) })
      const lesson = d.levels.flatMap((l) => l.topics).find((t) => t.isFreeTrial)?.lessons[0]
      const target = lesson ? `/bai-giang/${lesson.id}` : `/khoa-hoc/${slug}`
      nav(user ? target : `/dang-nhap?next=${encodeURIComponent(target)}`)
    } catch {
      setFailed(true)
    } finally {
      setBusy(null)
    }
  }
  return { open: (s) => void open(s), busy, failed }
}
