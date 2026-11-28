import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { LearningProfileView } from '../profile/LearningProfilePage'
import type { LearningProfile } from '../profile/api'
import { useBoard } from './api'
import { classPath } from './format'
import { useNum } from './ui'

// #62, FR-EX-09: hồ sơ năng lực của một học viên trong lớp, dùng lại phần hiển thị của học viên (404 nếu ngoài lớp).
export function StudentProfilePage() {
  const classId = useNum('classId')
  const userId = useNum('userId')
  const q = useQuery({
    queryKey: ['teaching', 'profile', classId, userId],
    queryFn: () => api<LearningProfile>(`/classes/${classId}/students/${userId}/learning-profile`),
    retry: false,
  })
  // Tên lấy từ bảng tiến độ (đã có trong cache khi đi từ đó vào).
  const name = useBoard(classId).data?.students.find((s) => s.userId === userId)?.displayName
  return (
    <LearningProfileView
      q={q}
      own={false}
      title={name ? `Hồ sơ năng lực · ${name}` : 'Hồ sơ năng lực học viên'}
      sub="Điểm mạnh, điểm yếu và tiến bộ của học viên, tính từ các bài đã làm."
      back={{ to: classPath(classId), label: 'Về tiến độ lớp' }}
    />
  )
}
