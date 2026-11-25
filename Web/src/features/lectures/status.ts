import { fold } from '../courses/format'
import type { MyCourse, RoadmapLesson, RoadmapLevel, RoadmapTopic, Skill } from '../home/types'

// D-59: Chưa xem = chưa có tiến độ; Đang xem = có tiến độ, chưa hoàn thành; Đã xem = server cờ completed (>= 80%).
export type LessonStatus = 'NEW' | 'WATCHING' | 'DONE'

export const lessonStatus = (l: Pick<RoadmapLesson, 'completed' | 'watchedSec' | 'maxPositionSec'>): LessonStatus =>
  l.completed ? 'DONE' : l.watchedSec > 0 || (l.maxPositionSec ?? 0) > 0 ? 'WATCHING' : 'NEW'

export const statusLabel: Record<LessonStatus, string> = { NEW: 'Chưa xem', WATCHING: 'Đang xem', DONE: 'Đã xem' }

// "Còn mm:ss" = thời lượng - vị trí đã dừng; null khi thiếu dữ liệu hoặc đã xem hết.
export const remainingSec = (durationSec: number | null, positionSec: number | null) =>
  durationSec == null || !positionSec ? null : Math.max(0, durationSec - positionSec)

export const formatClock = (sec: number) => {
  const s = Math.max(0, Math.round(sec))
  const h = Math.floor(s / 3600)
  const mm = String(Math.floor((s % 3600) / 60)).padStart(h ? 2 : 1, '0')
  const ss = String(s % 60).padStart(2, '0')
  return h ? `${h}:${mm}:${ss}` : `${mm.padStart(2, '0')}:${ss}`
}

export const percentOf = (part: number, total: number) => (total > 0 ? Math.min(100, (part / total) * 100) : 0)

export const skillLabel: Record<Skill, string> = {
  LISTENING: 'Listening',
  READING: 'Reading',
  WRITING: 'Writing',
  SPEAKING: 'Speaking',
  GRAMMAR_VOCAB: 'Ngữ pháp & Từ vựng',
}
const skillOrder = Object.keys(skillLabel) as Skill[]

// D-58: chip chỉ gồm kỹ năng có trong khóa, giữ thứ tự cố định.
export const skillsIn = (levels: RoadmapLevel[]): Skill[] => {
  const present = new Set(levels.flatMap((l) => l.topics.map((t) => t.skill)))
  return skillOrder.filter((s) => present.has(s))
}

export interface LessonFilter {
  query: string
  skill: Skill | null
  status: LessonStatus | null
}

// Tìm theo tên/trạng thái: chỉ giữ topic còn bài khớp; chỉ lọc kỹ năng thì giữ cả topic rỗng.
export function filterTopics(topics: RoadmapTopic[], f: LessonFilter): { topic: RoadmapTopic; lessons: RoadmapLesson[] }[] {
  const q = fold(f.query.trim())
  const active = !!q || !!f.status
  return topics
    .filter((t) => !f.skill || t.skill === f.skill)
    .map((topic) => ({
      topic,
      lessons: topic.lessons.filter((l) => (!q || fold(l.title).includes(q)) && (!f.status || lessonStatus(l) === f.status)),
    }))
    .filter(({ lessons }) => !active || lessons.length > 0)
}

export type TopicBadgeTone = 'success' | 'brand' | 'plain'
export function topicBadge(t: RoadmapTopic): { text: string; tone: TopicBadgeTone } {
  if (t.locked || t.status === 'LOCKED') return { text: 'Đã khóa', tone: 'plain' }
  if (t.status === 'DONE') return { text: 'Hoàn thành', tone: 'success' }
  if (t.completedLessons > 0 || t.status === 'IN_PROGRESS') return { text: `Đã xem ${t.completedLessons}/${t.totalLessons}`, tone: 'brand' }
  return { text: 'Chưa bắt đầu', tone: 'plain' }
}

// Trạng thái hiển thị của khóa trong màn chọn khóa.
export function courseChip(c: MyCourse): { text: string; tone: TopicBadgeTone } {
  if (c.status === 'EXPIRED') return { text: 'Đã hết hạn', tone: 'plain' }
  if (c.totalLessons > 0 && c.completedLessons >= c.totalLessons) return { text: 'Đã hoàn thành', tone: 'success' }
  if (c.completedLessons > 0) return { text: `Đang xem · ${c.completedLessons} / ${c.totalLessons} bài`, tone: 'brand' }
  return { text: 'Chưa bắt đầu', tone: 'plain' }
}

// Đường vào unit: bài chưa xong đầu tiên, không có thì bài đầu; thiếu dữ liệu thì về danh sách bài của khóa.
export function unitEntry(topics: RoadmapTopic[], topicId: number | null, courseId: number) {
  const lessons = topics.find((t) => t.id === topicId)?.lessons ?? []
  const next = lessons.find((l) => !l.completed) ?? lessons[0]
  return next ? `/bai-giang/${next.id}` : `/bai-giang/khoa/${courseId}`
}
