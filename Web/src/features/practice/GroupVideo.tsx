import type { BankGroup } from './types'

// D-156: video làm đề của nhóm. File mp4: link ký có hạn, chặn tải và menu chuột phải (NFR-SEC-02). YouTube: nhúng youtube-nocookie.
export function GroupVideo({ group }: { group: BankGroup }) {
  const frame = 'aspect-video w-full overflow-hidden rounded-lg bg-ink'
  if (group.videoUrl)
    return (
      <video
        aria-label="Video đề"
        src={group.videoUrl}
        controls
        controlsList="nodownload"
        disablePictureInPicture
        onContextMenu={(e) => e.preventDefault()}
        className={frame}
      />
    )
  if (group.videoYoutubeId)
    return (
      <iframe
        title="Video đề"
        src={`https://www.youtube-nocookie.com/embed/${encodeURIComponent(group.videoYoutubeId)}?rel=0&playsinline=1`}
        allow="encrypted-media; picture-in-picture"
        allowFullScreen
        referrerPolicy="strict-origin-when-cross-origin"
        className={frame}
      />
    )
  return null
}
