import { ApiError } from '../../../../lib/api'
import type { MediaView } from './types'

export const uid = () => Math.random().toString(36).slice(2, 10)
export const tokenize = (s: string) => s.split(/\s+/).filter(Boolean)

// Chỗ trống viết {id}; trả theo thứ tự xuất hiện, không lặp.
export const placeholders = (text: string): string[] => {
  const out: string[] = []
  for (const m of text.matchAll(/\{([^{}]+)\}/g)) if (!out.includes(m[1])) out.push(m[1])
  return out
}

export const nextId = (ids: string[], prefix: string) => {
  let n = ids.length + 1
  while (ids.includes(prefix + n)) n++
  return prefix + n
}

export const isEmptyHtml = (h: string) => !h.replace(/<[^>]*>/g, '').trim() && !/<img/.test(h)

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
// Dữ liệu cũ chỉ có chữ thuần: bọc thành đoạn để TipTap hiển thị được.
export const textToHtml = (s: string) => (s ? `<p>${esc(s)}</p>` : '')

// Backend chỉ nhận ảnh nội bộ qua data-asset-id nên bỏ src (URL ký có hạn) trước khi gửi.
export const stripImgSrc = (h: string) => h.replace(/(<img\b[^>]*?)\s+src="[^"]*"/g, '$1')

// Ngược lại: ảnh chỉ có data-asset-id thì gắn url từ danh sách media để editor hiển thị.
export const hydrateImages = (html: string, media: MediaView[]) =>
  html.replace(/<img\b[^>]*>/g, (tag) => {
    const id = /data-asset-id="(\d+)"/.exec(tag)?.[1]
    const m = media.find((x) => String(x.assetId) === id)
    return m && !/\ssrc=/.test(tag) ? tag.replace('<img', `<img src="${m.url}"`) : tag
  })

export type Details = Record<string, string>
export const detailsOf = (e: unknown): Details =>
  e instanceof ApiError && e.details && typeof e.details === 'object' ? (e.details as Details) : {}

// Lỗi của một ô hoặc cả nhánh: khóa trùng hoặc bắt đầu bằng key rồi tới '.' hay '['.
export const errAt = (d: Details, ...keys: string[]) =>
  Object.entries(d)
    .filter(([k]) => keys.some((key) => k === key || k.startsWith(`${key}.`) || k.startsWith(`${key}[`)))
    .map(([, v]) => v)
