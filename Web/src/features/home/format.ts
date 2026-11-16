import type { MyCourse, Roadmap, RoadmapLevel } from './types'

// Khóa đang học mặc định: ưu tiên ACTIVE, rồi học thử, cuối cùng hết hạn (D-61).
export const currentCourse = (courses?: MyCourse[]) =>
  courses?.find((c) => c.status === 'ACTIVE') ?? courses?.find((c) => c.status === 'TRIAL') ?? courses?.[0]

const VN_TZ = 'Asia/Ho_Chi_Minh'
export const formatXp = (xp: number) => `${xp.toLocaleString('vi-VN')} XP`

export const formatMmSs = (sec: number) =>
  `${String(Math.floor(sec / 60)).padStart(2, '0')}:${String(sec % 60).padStart(2, '0')}`

// Ngày hiện tại theo giờ VN dạng YYYY-MM-DD, không phụ thuộc múi giờ máy.
export const vnToday = (now = new Date()) =>
  new Intl.DateTimeFormat('en-CA', { timeZone: VN_TZ, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now)

export const WEEK_LABELS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']

// 7 ngày T2..CN của tuần chứa `today`.
export function weekDates(today: string) {
  const [y, m, d] = today.split('-').map(Number)
  const dow = (new Date(Date.UTC(y, m - 1, d)).getUTCDay() + 6) % 7 // T2 = 0
  return WEEK_LABELS.map((label, i) => ({
    label,
    date: new Date(Date.UTC(y, m - 1, d - dow + i)).toISOString().slice(0, 10),
  }))
}

// Level hiển thị: chứa resume; không có thì level cuối có tiến độ; không có nữa thì level đầu.
export function pickLevel(roadmap: Roadmap): RoadmapLevel | undefined {
  const { levels, resume } = roadmap
  const byResume = resume && levels.find((l) => l.id === resume.levelId)
  if (byResume) return byResume
  const started = levels.filter((l) => l.topics.some((t) => t.completedLessons > 0 || t.status === 'DONE'))
  return started.at(-1) ?? levels[0]
}
