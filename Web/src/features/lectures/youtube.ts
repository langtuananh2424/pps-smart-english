// Phần tối thiểu của YouTube IFrame API mà trình phát dùng.
export interface YtPlayer {
  getCurrentTime(): number
  getDuration(): number
  // Chỉ video làm đề khóa tua (practice/video) dùng ba hàm này; player bài giảng không gọi.
  seekTo(seconds: number, allowSeekAhead: boolean): void
  playVideo(): void
  pauseVideo(): void
  destroy(): void
}
export interface YtEvent {
  data: number
}
export interface YtApi {
  Player: new (
    el: HTMLElement,
    opts: {
      host: string
      videoId: string
      playerVars: Record<string, number>
      events: { onStateChange: (e: YtEvent) => void; onError: () => void }
    },
  ) => YtPlayer
}
// Mã trạng thái của YT.PlayerState.
export const YT_ENDED = 0
export const YT_PLAYING = 1
export const YT_PAUSED = 2

declare global {
  interface Window {
    YT?: YtApi
    onYouTubeIframeAPIReady?: () => void
  }
}

let loading: Promise<YtApi> | null = null

// Chỉ nạp script một lần cho cả trang.
export function loadYouTubeApi(): Promise<YtApi> {
  if (window.YT?.Player) return Promise.resolve(window.YT)
  loading ??= new Promise<YtApi>((resolve, reject) => {
    const prev = window.onYouTubeIframeAPIReady
    window.onYouTubeIframeAPIReady = () => {
      prev?.()
      resolve(window.YT as YtApi)
    }
    const s = document.createElement('script')
    s.src = 'https://www.youtube.com/iframe_api'
    s.async = true
    s.onerror = () => {
      loading = null
      reject(new Error('youtube_api'))
    }
    document.head.append(s)
  })
  return loading
}
