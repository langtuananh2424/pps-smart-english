import type { ProgressBody } from './api'

export const REPORT_INTERVAL_MS = 15_000
export const MAX_DELTA_SEC = 20 // trần của server; vượt thì bị từ chối.

// Gom thời gian phát thật và gửi tiến độ định kỳ khi đang phát, cùng khi tạm dừng/kết thúc/ẩn tab/rời trang.
export class ProgressTracker {
  private since: number | null = null
  private timer: ReturnType<typeof setInterval> | null = null
  private lastPosted: ProgressBody | null = null

  constructor(
    private readonly post: (body: ProgressBody, final: boolean) => void,
    private readonly position: () => number,
  ) {}

  play() {
    if (this.since != null) return
    this.since = Date.now()
    this.timer = setInterval(() => this.flush(false), REPORT_INTERVAL_MS)
  }

  // Dừng đếm giờ và gửi nốt phần còn lại.
  pause() {
    if (this.since == null) return
    this.flush(true)
    this.since = null
    if (this.timer) clearInterval(this.timer)
    this.timer = null
  }

  // Gửi ngay mà vẫn đếm tiếp (đổi tab).
  flush(final: boolean) {
    const now = Date.now()
    const deltaSec = this.since == null ? 0 : Math.min(MAX_DELTA_SEC, Math.round((now - this.since) / 1000))
    if (this.since != null) this.since = now
    const body = { positionSec: Math.floor(this.position()), watchedDeltaSec: deltaSec }
    const last = this.lastPosted
    // Không gửi lặp khi chưa có gì mới.
    if (deltaSec === 0 && last && last.positionSec === body.positionSec) return
    this.lastPosted = body
    this.post(body, final)
  }
}
