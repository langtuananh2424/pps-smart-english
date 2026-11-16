// Kiểu dữ liệu khớp JSON backend của catalog và gợi ý khóa.
export interface CourseSummary {
  slug: string
  title: string
  description: string
  targetType: string
  bandFrom: number
  bandTo: number
  trialLessonCount: number
  priceFromVnd: number | null
  levelCount: number
  lessonCount: number
}

export interface CourseDetail extends CourseSummary {
  levels: {
    id: number
    code: string
    title: string
    topics: {
      id: number
      title: string
      isFreeTrial: boolean
      lessons: { id: number; title: string; type: string; durationSec: number | null }[]
    }[]
  }[]
  products: { id: number; name: string; priceVnd: number; accessDays: number }[]
}

export interface CourseCardData {
  courseId: number
  slug: string
  title: string
  bandFrom: number
  bandTo: number
  trialLessonCount: number
  priceFromVnd: number | null
}

export interface Suggestions {
  basis: 'PLACEMENT' | 'SELF_LEVEL' | 'NONE'
  currentBand: number | null
  targetBand: number | null
  primary: CourseCardData | null
  path: CourseCardData[]
}

export interface Progress {
  completedLessons: number
  totalLessons: number
  percent: number
}
