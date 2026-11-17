// Chuyển audio bất kỳ (ghi từ micro hoặc tệp chọn) sang WAV PCM 16-bit 16 kHz mono (D-85, backend chỉ nhận định dạng này).
export const TARGET_RATE = 16_000
export const MIN_SECONDS = 1
// Ghi âm tự dừng đúng maxSeconds nên có thể lố vài chục ms; backend cho thêm 2 giây.
export const SLACK_SECONDS = 0.5
// Chặn tệp quá lớn trước khi giải mã vào bộ nhớ (mp3 128 kbps ~ 4 giờ).
export const MAX_INPUT_BYTES = 30 * 1024 * 1024

export type AudioIssue = 'too_short' | 'too_long' | 'too_big' | 'undecodable'
export class AudioError extends Error {
  constructor(readonly issue: AudioIssue) {
    super(issue)
  }
}

const tag = (v: DataView, at: number, s: string) => [...s].forEach((c, i) => v.setUint8(at + i, c.charCodeAt(0)))

// Header RIFF chuẩn 44 byte, fmt = 1 (PCM), mono, 16 bit; mẫu float [-1,1] được cắt rồi nhân 32767.
export function encodeWav(samples: Float32Array, sampleRate = TARGET_RATE): ArrayBuffer {
  const dataLen = samples.length * 2
  const buf = new ArrayBuffer(44 + dataLen)
  const v = new DataView(buf)
  tag(v, 0, 'RIFF')
  v.setUint32(4, 36 + dataLen, true)
  tag(v, 8, 'WAVE')
  tag(v, 12, 'fmt ')
  v.setUint32(16, 16, true)
  v.setUint16(20, 1, true)
  v.setUint16(22, 1, true)
  v.setUint32(24, sampleRate, true)
  v.setUint32(28, sampleRate * 2, true)
  v.setUint16(32, 2, true)
  v.setUint16(34, 16, true)
  tag(v, 36, 'data')
  v.setUint32(40, dataLen, true)
  samples.forEach((s, i) => v.setInt16(44 + i * 2, Math.round(Math.max(-1, Math.min(1, s)) * 32767), true))
  return buf
}

export interface Converted { wav: Blob; seconds: number }

// decodeAudioData → OfflineAudioContext 1 kênh 16 kHz (trình duyệt tự trộn mono và lấy lại mẫu) → WAV.
export async function toWav16k(input: Blob, maxSeconds: number): Promise<Converted> {
  if (input.size > MAX_INPUT_BYTES) throw new AudioError('too_big')
  const ctx = new AudioContext()
  let decoded: AudioBuffer
  try {
    decoded = await ctx.decodeAudioData(await input.arrayBuffer())
  } catch {
    throw new AudioError('undecodable')
  } finally {
    void ctx.close().catch(() => {})
  }
  if (decoded.duration < MIN_SECONDS) throw new AudioError('too_short')
  if (decoded.duration > maxSeconds + SLACK_SECONDS) throw new AudioError('too_long')
  const off = new OfflineAudioContext(1, Math.ceil(decoded.duration * TARGET_RATE), TARGET_RATE)
  const src = off.createBufferSource()
  src.buffer = decoded
  src.connect(off.destination)
  src.start()
  const samples = (await off.startRendering()).getChannelData(0)
  return { wav: new Blob([encodeWav(samples)], { type: 'audio/wav' }), seconds: samples.length / TARGET_RATE }
}
