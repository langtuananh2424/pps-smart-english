import type { Started } from '../practice/types'

export type ExtraKind = 'AUTO' | 'WRITING' | 'SPEAKING'
export type CardState = 'COMPLETED' | 'OPEN' | 'PAID' | 'LOCKED'

// D-152: thẻ Topic. available = số câu hợp lệ theo dạng (học thử chỉ có AUTO), chỉ có ở COMPLETED/OPEN.
export interface ExtraCard {
  topicId: number
  title: string
  levelId: number
  levelTitle: string
  levelNo: number
  topicNo: number
  state: CardState
  accuracy?: number | null
  questions?: number | null
  available?: Record<ExtraKind, number>
  trialOnly: boolean
  blockedByTopicId?: number
  blockedByTitle?: string
}

export interface ExtraOverview {
  courseId: number
  courseSlug: string
  courseTitle: string
  reviewSize: number
  challengeSize: number
  maxRatio: number
  levels: { id: number; title: string }[]
  review: ExtraCard[]
  challenge: ExtraCard[]
}

export interface StartExtra { scope: 'REVIEW' | 'CHALLENGE'; topicId?: number; kind?: ExtraKind }
export type { Started }
