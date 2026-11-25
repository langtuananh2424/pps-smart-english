import { SKILLS, type BandRule, type BankQuestion } from './api'

export interface RuleRow {
  skill: string
  minPercent: string
  band: string
}

// Kiểm phía client cho bảng quy đổi (BE kiểm lại): 0..100, band 0..9 bước 0,5, không trùng (kỹ năng, minPercent).
export function validateRules(rows: RuleRow[]): { rules: BandRule[]; errors: string[] } {
  const errors: string[] = []
  const seen = new Set<string>()
  const rules: BandRule[] = []
  rows.forEach((r, i) => {
    const at = `Dòng ${i + 1}`
    const min = Number(r.minPercent)
    const band = Number(r.band.replace(',', '.'))
    if (!SKILLS.includes(r.skill as (typeof SKILLS)[number])) errors.push(`${at}: kỹ năng không hợp lệ`)
    if (r.minPercent.trim() === '' || !Number.isInteger(min) || min < 0 || min > 100) errors.push(`${at}: % đúng tối thiểu phải là số nguyên từ 0 đến 100`)
    if (r.band.trim() === '' || !(band >= 0 && band <= 9) || (band * 2) % 1 !== 0) errors.push(`${at}: band từ 0 đến 9, bước 0,5`)
    const key = `${r.skill}:${min}`
    if (seen.has(key)) errors.push(`${at}: trùng mốc ${r.skill} ${min}%`)
    seen.add(key)
    rules.push({ skill: r.skill, minPercent: min, band })
  })
  return { rules, errors }
}

// Điều kiện xuất bản kiểm phía client (BE không có API xem trước): ≥1 câu; đề đầu vào cần bảng quy đổi cho mỗi kỹ năng có trong đề.
export function publishBlockers(count: number, placement: boolean, questions: BankQuestion[], rules: BandRule[]): string[] {
  const out: string[] = []
  if (count < 1) out.push('Cần ít nhất 1 câu hỏi trước khi xuất bản.')
  if (placement) {
    const have = new Set(rules.map((r) => r.skill))
    for (const s of new Set(questions.map((q) => q.skill))) if (!have.has(s)) out.push(`Thiếu bảng quy đổi band cho kỹ năng ${s}.`)
  }
  return out
}
