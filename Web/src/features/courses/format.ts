// Band hiển thị 1 chữ số thập phân; 0 hiện "0".
export const formatBand = (n: number) => (n === 0 ? '0' : n.toFixed(1))
export const bandRange = (from: number, to: number) => `${formatBand(from)} → ${formatBand(to)}`
export const formatPrice = (vnd: number) => `Từ ${vnd.toLocaleString('vi-VN')} đ`

// So khớp không phân biệt hoa thường và dấu tiếng Việt (U+0300–U+036F là dấu kết hợp sau NFD).
export const fold = (s: string) =>
  s
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/gi, 'd')
    .toLowerCase()
