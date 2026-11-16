import { z } from 'zod'

// Ràng buộc khớp PUT /api/me/qa (D-55).
export const selfLevelSchema = z.enum(['MAT_GOC', 'CO_BAN', 'TRUNG_CAP', 'KHA'])
export const goalSchema = z.enum(['IELTS_CERT', 'STUDY_ABROAD', 'WORK', 'SCHOOL', 'UNSURE'])
export const hoursSchema = z.enum(['LT3', 'H3_5', 'H5_10', 'GT10'])
export const skillSchema = z.enum(['LISTENING', 'SPEAKING', 'READING', 'WRITING'])
// 4.0..8.0, bước 0.5
export const bandSchema = z.number().min(4).max(8).refine((n) => Number.isInteger(n * 2))

export const qaSchema = z.object({
  selfLevel: selfLevelSchema,
  goals: z.array(goalSchema).min(1).max(2),
  targetBand: bandSchema,
  weeklyHours: hoursSchema,
  skills: z.array(skillSchema).max(4),
})

export type QaAnswers = z.infer<typeof qaSchema>
export type Goal = z.infer<typeof goalSchema>
export type Draft = Partial<QaAnswers>

// Mỗi bước kiểm tra phần câu trả lời của riêng nó.
export const stepSchemas = [
  qaSchema.pick({ selfLevel: true }),
  qaSchema.pick({ goals: true }),
  qaSchema.pick({ targetBand: true }),
  qaSchema.pick({ weeklyHours: true, skills: true }),
]
