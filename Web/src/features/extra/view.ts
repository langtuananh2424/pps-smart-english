import type { ExtraCard, ExtraKind, ExtraOverview } from './types'

export const KIND_LABEL: Record<ExtraKind, string> = { AUTO: 'Tự chấm', WRITING: 'Viết', SPEAKING: 'Nói' }

// Cùng công thức máy chủ: min(n, ceil(ratio * tổng)), tối thiểu 1.
export const capped = (n: number, total: number, ratio: number) => Math.max(1, Math.min(n, Math.ceil(ratio * total - 1e-9)))

export const inLevel = (cards: ExtraCard[], levelId: number | null) => (levelId == null ? cards : cards.filter((c) => c.levelId === levelId))

// Số câu hợp lệ của thẻ theo dạng (null = mọi dạng).
export const availableOf = (c: ExtraCard, kind: ExtraKind | null) =>
  !c.available ? 0 : kind ? c.available[kind] : c.available.AUTO + c.available.WRITING + c.available.SPEAKING

// "Luyện N câu" của Ôn lại: trần theo tổng câu của dạng đó trong các Topic đã hoàn thành (trong Level đang lọc).
export function reviewCount(o: ExtraOverview, cards: ExtraCard[], kind: ExtraKind | null) {
  const total = cards.reduce((s, c) => s + availableOf(c, kind), 0)
  return total === 0 ? 0 : capped(o.reviewSize, total, o.maxRatio)
}

// "Thử sức N câu": trần theo tổng câu của cả Topic; 0 khi Topic chưa có câu thuộc dạng đang lọc.
export function challengeCount(o: ExtraOverview, c: ExtraCard, kind: ExtraKind | null) {
  if (availableOf(c, kind) === 0) return 0
  return capped(o.challengeSize, availableOf(c, null), o.maxRatio)
}

export const cardLabel = (c: ExtraCard) => `${c.levelTitle} · Topic ${c.topicNo}`
