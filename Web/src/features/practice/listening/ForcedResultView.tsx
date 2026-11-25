import { useState } from 'react'
import { Link } from 'react-router'
import cam from '../../../assets/exercises/cam-kinh-lup.webp'
import { ApiError } from '../../../lib/api'
import { chip } from '../../lectures/ui'
import { fetchScript } from '../api'
import { pillNeutral, pillPrimary } from '../questions/ui'
import type { Bank, ForcedResult, ScriptView } from '../types'

interface Props {
  bank: Bank
  attemptId: number
  forced: ForcedResult
  onRetry: () => void
}

// D-142: Nghe ép chỉ có số câu đúng và đạt/chưa đạt, tuyệt đối không hiện đáp án đúng.
export function ForcedResultView({ bank, attemptId, forced: f, onRetry }: Props) {
  return (
    <div className="min-h-dvh bg-page px-4 py-4">
      <main className="mx-auto flex max-w-[900px] flex-col gap-[18px]">
        <header>
          <p className="text-[13px] font-extrabold leading-[18px] text-brand-strong">BÀI TẬP · Lần làm thứ {f.attemptNo}</p>
          <h1 className="text-2xl font-black leading-[30px] text-ink md:text-[30px] md:leading-[38px]">{bank.title}</h1>
        </header>

        <section aria-label="Kết quả" className="flex flex-col items-center gap-2.5 p-6 text-center">
          <img src={cam} alt="" width={120} height={120} />
          <p className={`text-3xl font-black ${f.passed ? 'text-success-fg' : 'text-danger-fg'}`}>{f.passed ? 'Đạt yêu cầu' : 'Chưa đạt yêu cầu'}</p>
          <p className="text-5xl font-black text-brand-strong">
            {f.correctCount}
            <span className="text-2xl text-ink-muted">/{f.totalCount}</span>
          </p>
          <p className="text-base font-semibold text-ink-secondary">
            Đúng {Math.round(f.percent)}% số câu{f.passPct != null && ` · Ngưỡng ${f.passPct}%`}
          </p>
        </section>

        {f.parts.length > 0 && (
          <section aria-label="Theo từng phần" className="flex flex-col gap-2.5">
            <h2 className="text-lg font-black text-ink">Theo từng phần</h2>
            <ul className="flex flex-col gap-2.5">
              {f.parts.map((p) => (
                <li key={p.groupId} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border-[1.5px] border-line bg-surface px-4 py-3">
                  <div>
                    <p className="text-base font-black text-ink">{p.title ?? 'Phần nghe'}</p>
                    {p.passPct != null && <p className="text-[13px] font-semibold text-ink-secondary">Ngưỡng {p.passPct}%</p>}
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-base font-black text-ink">{p.correct}/{p.total}</span>
                    {p.passed != null && <span className={`${chip} ${p.passed ? 'bg-success-bg text-success-fg' : 'bg-danger-bg text-danger-fg'}`}>{p.passed ? 'Đạt' : 'Chưa đạt'}</span>}
                  </div>
                </li>
              ))}
            </ul>
          </section>
        )}

        {f.hardQuestions && f.hardQuestions.length > 0 && (
          <section aria-label="Câu sai nhiều" className="flex flex-col gap-2.5">
            <h2 className="text-lg font-black text-ink">Câu sai nhiều</h2>
            <ul className="flex flex-col gap-2.5">
              {f.hardQuestions.map((h) => (
                <HardRow key={h.questionId} attemptId={attemptId} item={h} />
              ))}
            </ul>
          </section>
        )}

        <div className="flex flex-wrap justify-center gap-3 pb-4">
          <button type="button" onClick={onRetry} className={`${pillPrimary} w-full md:w-[332px]`}>Làm lại</button>
          <Link to="/bai-tap" className={`${pillNeutral} w-full md:w-auto`}>Về danh sách bài tập</Link>
        </div>
      </main>
    </div>
  )
}

function HardRow({ attemptId, item }: { attemptId: number; item: NonNullable<ForcedResult['hardQuestions']>[number] }) {
  const [script, setScript] = useState<ScriptView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  async function open() {
    setBusy(true)
    setError(null)
    try {
      setScript(await fetchScript(attemptId, item.questionId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Không tải được script, thử lại.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <li className="flex flex-col gap-2 rounded-lg border-[1.5px] border-line bg-surface p-4">
      <p className="text-base font-black text-ink">{item.prompt}</p>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className={`${chip} bg-danger-bg text-danger-fg`}>Sai {item.wrongCount} lần</span>
        {script ? (
          <button type="button" onClick={() => setScript(null)} className="text-sm font-black text-brand-strong underline">Đóng script</button>
        ) : (
          <button type="button" disabled={busy} onClick={() => void open()} className="text-sm font-black text-brand-strong underline disabled:opacity-60">Xem script</button>
        )}
      </div>
      {error && <p role="alert" className="rounded-md bg-danger-bg px-3 py-2 text-sm font-bold text-danger">{error}</p>}
      {script &&
        (script.html ? (
          // html đã được máy chủ làm sạch.
          <div className="text-base font-semibold text-ink" dangerouslySetInnerHTML={{ __html: script.html }} />
        ) : (
          <p className="whitespace-pre-line text-base font-semibold text-ink">{script.text}</p>
        ))}
    </li>
  )
}
