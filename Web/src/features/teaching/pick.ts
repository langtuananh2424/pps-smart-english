// Bài được chọn ở bước 1: bộ câu (bankId) hoặc một câu Viết/Nói (questionId), đúng một trong hai (D-130).
export interface Picked {
  key: string
  title: string
  bankId?: number
  kind?: 'PRACTICE' | 'EXAM' // chế độ của bộ
  questionId?: number
  type?: 'WRITING' | 'SPEAKING'
}
export const TYPE_WORD = { WRITING: 'Viết', SPEAKING: 'Nói' } as const
