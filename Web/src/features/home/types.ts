// Kiểu dữ liệu khớp JSON backend của trang chủ học viên.
export interface MeStats {
  xp: number
  streakCurrent: number
  streakBest: number
  lastActiveDate: string | null
  title: string
  nextTitle: string | null
  nextTitleXp: number | null
  activeToday: boolean
  weekDays: string[] // YYYY-MM-DD, ngày đã học trong tuần T2-CN (giờ VN)
}

export interface MyCourse {
  courseId: number
  slug: string
  title: string
  targetType: string
  status: 'ACTIVE' | 'EXPIRED' | 'TRIAL'
  expiresAt: string | null
  lastActivityAt: string | null
  completedLessons: number
  totalLessons: number
  answeredQuestions: number
  correctQuestions: number
}

export type TopicStatus = 'DONE' | 'IN_PROGRESS' | 'NOT_STARTED' | 'LOCKED'

export type Skill = 'LISTENING' | 'READING' | 'WRITING' | 'SPEAKING' | 'GRAMMAR_VOCAB'

export interface RoadmapLesson {
  id: number
  title: string
  type: string
  durationSec: number | null
  order: number
  completed: boolean
  watchedSec: number
  maxPositionSec: number | null
  lastWatchedAt: string | null
}

export interface RoadmapBank {
  id: number
  title: string
  lessonId: number
  questionCount: number
}

export interface RoadmapTopic {
  id: number
  title: string
  skill: Skill | null
  isFreeTrial: boolean
  locked: boolean
  completedLessons: number
  totalLessons: number
  xpEarned: number
  status: TopicStatus
  lessons: RoadmapLesson[]
  banks: RoadmapBank[]
}

export interface RoadmapLevel {
  id: number
  code: string
  title: string
  topics: RoadmapTopic[]
}

export interface Resume {
  lessonId: number
  lessonTitle: string
  lessonOrder: number
  topicId: number
  topicTitle: string
  levelId: number
  levelCode: string
  levelTitle: string
  completedInTopic: number
  totalInTopic: number
  positionSec: number
  durationSec: number | null
}

export interface Roadmap {
  levels: RoadmapLevel[]
  resume: Resume | null
}

export interface TodayTask {
  kind: 'VIDEO' | 'EXERCISE' | 'WRITING'
  title: string
  subtitle: string | null
  lessonId: number | null
  bankId: number | null
  questionId: number | null
  remainingSec: number | null
  questionCount: number | null
  doneToday: boolean
}

export interface Today {
  courseId: number
  tasks: TodayTask[]
  doneCount: number
  total: number
}

export interface Quota {
  pool: number
  courses: { courseId: number; courseTitle: string; period: string; quota: number; used: number; remaining: number; resetsAt: string }[]
}

export interface CamMessage {
  kind: 'GREETING' | 'IN_PROGRESS_ATTEMPT' | 'STREAK' | 'COURSE_EXPIRING' | 'UNREAD'
  text: string
  action: { label: string; path: string } | null
}
