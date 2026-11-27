// Âm lượng bản ghi WAV 16-bit mono: đỉnh biên độ thấp hơn ngưỡng thì cảnh báo "âm lượng hơi nhỏ" (không chặn nộp).
export const QUIET_PEAK = 0.1

export async function isQuiet(wav: Blob, threshold = QUIET_PEAK): Promise<boolean> {
  const buf = await wav.arrayBuffer()
  const pcm = new Int16Array(buf.slice(44, 44 + Math.floor((buf.byteLength - 44) / 2) * 2))
  let peak = 0
  for (const s of pcm) peak = Math.max(peak, Math.abs(s))
  return peak / 32768 < threshold
}
