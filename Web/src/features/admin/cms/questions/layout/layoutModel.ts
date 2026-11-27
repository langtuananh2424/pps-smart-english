import type { IdText } from '../fields'
import type { Obj } from '../types'
import { stripImgSrc } from '../util'

export const LAYOUT_KINDS = [
  ['OPTIONS', 'Danh sách lựa chọn (heading, đoạn A-H...)'],
  ['TEXT', 'Đoạn văn có chỗ trống'],
  ['TEXT_BANK', 'Đoạn văn có hộp từ'],
  ['NOTE', 'Ghi chú (note)'],
  ['TABLE', 'Bảng'],
  ['FLOW', 'Các bước (flow-chart)'],
  ['FORM', 'Phiếu thông tin'],
  ['DIAGRAM', 'Sơ đồ trên ảnh'],
  ['MAP', 'Bản đồ / mặt bằng'],
] as const
export type LayoutKind = (typeof LAYOUT_KINDS)[number][0]

// Trần phía giao diện bằng mặc định app.exercise-layout.*; server vẫn là nơi kiểm cuối.
export const LIMITS = { rows: 50, cols: 10, options: 30, points: 30 }

export function emptyLayout(kind: LayoutKind): Obj {
  switch (kind) {
    case 'OPTIONS':
      return { kind, options: [{ id: 'o1', text: '' }, { id: 'o2', text: '' }], reuse: false }
    case 'TEXT':
      return { kind, html: '' }
    case 'TEXT_BANK':
      return { kind, html: '', words: [{ id: 'w1', text: '' }, { id: 'w2', text: '' }], reuse: false }
    case 'NOTE':
      return { kind, lines: [{ html: '' }] }
    case 'TABLE':
      return { kind, header: ['', ''], rows: [['', '']] }
    case 'FLOW':
      return { kind, steps: [] }
    case 'FORM':
      return { kind, rows: [{ label: '' }] }
    case 'DIAGRAM':
      return { kind, assetId: null, image: null, points: [] }
    case 'MAP':
      return { kind, assetId: null, image: null, options: [{ id: 'A', x: 25, y: 25 }, { id: 'B', x: 75, y: 75 }] }
  }
}

const SLOT = /\{([A-Za-z0-9_-]{1,40})\}/g
const scan = (s: string) => [...s.matchAll(SLOT)].map((m) => m[1])

// Cùng luật với LayoutIndex ở backend: các chỗ trống mà câu có thể gắn vào.
export function layoutSlots(l: Obj | null | undefined): string[] {
  if (!l) return []
  const raw: string[] =
    l.kind === 'TEXT' || l.kind === 'TEXT_BANK'
      ? scan(l.html ?? '')
      : l.kind === 'NOTE'
        ? (l.lines ?? []).flatMap((x: Obj) => scan(x.html ?? ''))
        : l.kind === 'TABLE'
          ? (l.rows ?? []).flat().flatMap(scan)
          : l.kind === 'FLOW'
            ? (l.steps ?? []).flatMap(scan)
            : l.kind === 'FORM'
              ? (l.rows ?? []).map((r: Obj) => r.slot)
              : l.kind === 'DIAGRAM'
                ? (l.points ?? []).map((p: Obj) => p.slot)
                : []
  return [...new Set(raw.filter(Boolean))]
}

export const layoutChoices = (l: Obj | null | undefined, name: 'options' | 'words'): Partial<IdText>[] => (l?.[name] as Partial<IdText>[] | undefined) ?? []

// Bản gửi lên: bỏ image (server tự thêm khi trả), bỏ src ảnh ký sẵn trong HTML, bỏ tiêu đề rỗng.
export function toLayoutBody(l: Obj | null): Obj | null {
  if (!l) return null
  const out: Obj = { ...l }
  delete out.image
  if (!out.title?.trim()) delete out.title
  if (typeof out.html === 'string') out.html = stripImgSrc(out.html)
  if (out.lines) out.lines = out.lines.map((x: Obj) => ({ html: stripImgSrc(x.html ?? '') }))
  return out
}
