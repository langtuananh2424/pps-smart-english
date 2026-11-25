import type { CourseTest } from './types'

export interface TestAction {
  label: string
  to: string
}

// attemptsAllowed gồm cả lượt đầu: số lần làm lại = allowed - 1 (0 = không cho làm lại, không phải không giới hạn).
export function retakesLabel(t: CourseTest) {
  const allowed = t.attemptsAllowed - 1
  return allowed <= 0 ? 'Không làm lại' : `${Math.max(t.attemptsUsed - 1, 0)}/${allowed}`
}

export function statusLines(t: CourseTest, requiredPercent: number | null): string[] {
  if (t.reason === 'ENROLLMENT_REQUIRED') return ['Chỉ dành cho học viên đã mua']
  if (t.reason === 'NOT_CONFIGURED') return ['Chưa cấu hình']
  const lines: string[] = []
  if (t.inProgressAttemptId != null) lines.push('Đang làm')
  else if (t.lastResult) lines.push('Đã làm · Xem điểm')
  if (t.lastResult?.score != null) lines.push(`Điểm ${t.lastResult.score}${t.lastResult.maxScore != null ? `/${t.lastResult.maxScore}` : ''}`)
  if (t.reason === 'UNIT_LOCKED') lines.push('Hoàn thành Unit trước')
  if (t.reason === 'PROGRESS') lines.push(requiredPercent != null ? `Khóa · Cần hoàn thành ${requiredPercent}% khóa` : 'Khóa · Chưa đủ tiến độ khóa')
  if (t.reason === 'ATTEMPTS_EXHAUSTED') lines.push('Hết lượt làm lại')
  return lines.length ? lines : ['Chưa làm']
}

// Chưa mua: chỉ có CTA mua, không dẫn vào màn làm bài.
export function actionsOf(t: CourseTest, courseId: number, slug: string | undefined): TestAction[] {
  if (t.reason === 'ENROLLMENT_REQUIRED') return [{ label: 'Mua khóa', to: slug ? `/thanh-toan?khoa=${slug}` : '/khoa-hoc' }]
  if (t.reason === 'UNIT_LOCKED' && t.topicId != null) return [{ label: 'Xem điều kiện', to: `/bai-giang/khoa/${courseId}/unit/${t.topicId}` }]
  if (t.bankId == null) return []
  const bank = `/bai-tap/${t.bankId}?khoa=${courseId}`
  if (t.inProgressAttemptId != null) return [{ label: 'Làm tiếp', to: bank }]
  const out: TestAction[] = []
  if (t.lastResult) out.push({ label: 'Xem kết quả', to: `/bai-tap/khoa/${courseId}/kiem-tra/${t.lastResult.attemptId}/ket-qua` })
  if (t.unlocked) out.push({ label: t.lastResult ? 'Làm lại' : 'Làm bài', to: bank })
  return out
}
