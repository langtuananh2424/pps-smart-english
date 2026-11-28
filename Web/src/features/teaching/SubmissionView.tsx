import type { Submission } from './submissionApi'
import { Section } from '../admin/ui'

// Bài làm của học viên và nhận xét của Cam (D-141).
// shortcut: hiển thị gọn thay vì dùng WritingResult/SpeakingResult (hai màn đó gắn với nút Viết lại, điều hướng của học viên); tách phần hiển thị khi cần chấm theo tiêu chí.
export function SubmissionView({ s }: { s: Submission }) {
  const w = s.writing
  const sp = s.speaking
  const r = sp?.result
  return (
    <>
      <section className="flex flex-col gap-2 rounded-lg border border-line-brand bg-subtle p-5">
        <h2 className="text-lg font-black text-brand-strong">Bài làm của học viên</h2>
        {w && <p className="whitespace-pre-wrap font-semibold text-ink">{w.finalText ?? w.draft}</p>}
        {sp && (
          <>
            {s.audioDeleted || !sp.audioUrl ? (
              <p className="font-semibold text-ink-secondary">Bản ghi đã xóa</p>
            ) : (
              <audio controls src={sp.audioUrl} aria-label="Bản ghi âm của học viên" className="w-full" />
            )}
            {r?.transcript && <p className="whitespace-pre-wrap font-semibold text-ink">{r.transcript}</p>}
          </>
        )}
      </section>
      <Section title="Nhận xét của Cam">
        {w?.grade && (
          <>
            <p className="font-extrabold text-ink">Điểm: {Math.round(w.grade.scorePercent)}%</p>
            {w.grade.feedback && <p className="whitespace-pre-wrap font-semibold text-ink-secondary">{w.grade.feedback}</p>}
          </>
        )}
        {sp && (r?.status === 'GRADED' ? (
          <>
            {r.overall != null && <p className="font-extrabold text-ink">Điểm: {r.overall}</p>}
            {r.feedback && <p className="whitespace-pre-wrap font-semibold text-ink-secondary">{r.feedback}</p>}
          </>
        ) : (
          <p className="font-semibold text-ink-secondary">Chưa có nhận xét.</p>
        ))}
        {w && !w.grade && <p className="font-semibold text-ink-secondary">Cam đang chấm bài.</p>}
      </Section>
    </>
  )
}
