import { useState } from 'react'
import { canRecord } from '../../speaking/useRecorder'
import { pillPrimary } from '../questions/ui'

const row = 'flex flex-col gap-1 rounded-md border-[1.5px] border-line bg-surface px-3.5 py-3'

// Màn "Bắt đầu làm bài" (Figma 10.28–10.31): xin quyền micro trước, rồi mới mở toàn màn hình (D-158: chưa giám sát).
export function ReflexStart({ type, onStart }: { type: string; onStart: () => void }) {
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const start = async () => {
    setBusy(true)
    setError(null)
    try {
      const s = await navigator.mediaDevices.getUserMedia({ audio: true })
      s.getTracks().forEach((t) => t.stop()) // chỉ để xin quyền, bản ghi thật mở lại ở bước nói
      onStart()
    } catch {
      setError('Bạn chưa cho phép micro. Hãy bật quyền micro của trình duyệt rồi thử lại.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <section aria-label="Bắt đầu làm bài" className="mx-auto flex max-w-[560px] flex-col gap-3 rounded-lg border-[1.5px] border-line bg-surface p-6">
      <h2 className="text-2xl font-black text-ink">Bắt đầu làm bài</h2>
      <p className="text-base font-semibold text-ink-secondary">Video tự dừng ở từng mốc. Bạn trả lời rồi xem tiếp.</p>
      <div className={row}><b className="text-ink">01 · Cho phép micro</b><span className="text-sm font-semibold text-ink-secondary">Micro được xin trước khi mở toàn màn hình.</span></div>
      <div className={row}>
        <b className="text-ink">{type === 'VIDEO_SHORT' ? '02 · Viết, nghe lại rồi nói' : '02 · Nghe và ghi âm'}</b>
        <span className="text-sm font-semibold text-ink-secondary">{type === 'VIDEO_SHORT' ? 'Trả lời bằng một câu ngắn rồi luyện nói.' : 'Nghe câu hỏi ở mốc rồi ghi âm câu trả lời.'}</span>
      </div>
      {!canRecord() && <p role="alert" className="text-sm font-bold text-danger-fg">Trình duyệt này chưa hỗ trợ ghi âm.</p>}
      {error && <p role="alert" className="rounded-md bg-danger-bg p-3 text-sm font-bold text-danger-fg">{error}</p>}
      <button type="button" disabled={busy || !canRecord()} onClick={() => void start()} className={`${pillPrimary} self-start`}>Bắt đầu</button>
    </section>
  )
}
