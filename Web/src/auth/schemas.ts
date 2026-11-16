import { z } from 'zod'

export const emailSchema = z.string().trim().min(1, 'Vui lòng nhập email').email('Email không hợp lệ')

// Backend: tối thiểu 8 ký tự, tối đa 72 byte UTF-8 (giới hạn BCrypt).
export const passwordSchema = z
  .string()
  .min(8, 'Mật khẩu tối thiểu 8 ký tự')
  .refine((p) => new TextEncoder().encode(p).length <= 72, 'Mật khẩu tối đa 72 byte')

// Dùng với .superRefine cho form có `password` + `confirm`.
export const confirmMatches = (v: { password: string; confirm: string }, ctx: z.RefinementCtx) => {
  if (v.password !== v.confirm) ctx.addIssue({ code: 'custom', path: ['confirm'], message: 'Mật khẩu nhập lại không khớp' })
}
