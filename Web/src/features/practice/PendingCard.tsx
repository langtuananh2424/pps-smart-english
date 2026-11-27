import { useState } from 'react'
import { Link } from 'react-router'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import camChao from '../../assets/cam-chao.png'
import { chip } from '../lectures/ui'
import { pillPrimary } from './questions/ui'

interface Props {
  title: string
  pending: number
  total: number // số câu đã gửi chấm
  gaveUp: boolean
  onReload: () => void
  notice?: React.ReactNode
}

// Màn "Đã nộp · Chờ chấm" (Figma 608-17733): còn câu Viết/Nói chưa chốt thì chưa có điểm (D-147).
export function PendingCard({ title, pending, total, gaveUp, onReload, notice }: Props) {
  const [bubble, setBubble] = useState(true)
  const all = Math.max(total, pending)
  return (
    <div className="min-h-dvh bg-page px-4 py-4 pb-[104px]">
      <main className="mx-auto flex max-w-[900px] flex-col gap-[18px]">
        <header className="flex items-center gap-3">
          <Link to="/bai-tap" aria-label="Về danh sách bài tập" className="grid size-12 shrink-0 place-items-center rounded-md border-[1.5px] border-line bg-surface text-[28px] font-black leading-5 text-brand-strong">
            ‹
          </Link>
          <div>
            <p className="text-[13px] font-extrabold leading-[18px] text-brand-strong">BÀI TẬP</p>
            <h1 className="text-2xl font-black leading-[30px] text-ink md:text-[30px] md:leading-[38px]">Đã nộp · Chờ chấm</h1>
            <p className="sr-only">{title}</p>
          </div>
        </header>
        <section aria-label="Điểm" className="flex flex-col items-center gap-2.5 py-10 text-center">
          <img src={cam} alt="" width={120} height={120} />
          <h2 className="text-2xl font-black text-ink">Bạn đã nộp bài!</h2>
          {gaveUp ? (
            <p className="max-w-[540px] text-base font-semibold text-ink-secondary">
              Chấm chưa xong.{' '}
              <button type="button" onClick={onReload} className="font-black text-brand-strong underline">Tải lại</button>
            </p>
          ) : (
            <p role="status" className="max-w-[540px] text-base font-semibold text-ink-secondary">
              Cam đang chấm {pending} câu. Điểm và kết quả Đạt/Chưa đạt sẽ hiện khi chấm xong.
            </p>
          )}
          <div className="flex gap-2">
            <span className={`${chip} bg-success-bg text-success-fg`}>✓ Đã nộp</span>
            <span className={`${chip} bg-muted text-ink`}>Đang chấm {all - pending}/{all}</span>
          </div>
          {notice}
        </section>
        <div className="fixed inset-x-0 bottom-0 flex justify-center bg-page px-4 pb-4 pt-2">
          <Link to="/bai-tap" className={`${pillPrimary} w-full md:w-[332px]`}>Về danh mục →</Link>
        </div>
      </main>
      {bubble && (
        <aside aria-label="Cam" className="fixed bottom-[88px] right-4 z-10 flex max-w-[calc(100vw-2rem)] flex-col items-end gap-2 md:bottom-[96px] md:right-7">
          <div className="flex w-[282px] flex-col gap-1 rounded-[20px] border border-line bg-surface p-3.5 shadow-edge-card">
            <div className="flex items-center justify-between">
              <h2 className="text-[15px] font-extrabold text-brand-strong">Cam</h2>
              <button type="button" aria-label="Đóng" onClick={() => setBubble(false)} className="grid size-9 place-items-center rounded-[10px] bg-input text-[13px] font-extrabold text-ink">×</button>
            </div>
            <p className="text-sm font-semibold text-ink">Có kết quả mình sẽ báo bạn nhé!</p>
          </div>
          <img src={camChao} alt="" className="size-[60px] rounded-full border border-line bg-surface p-0.5 md:size-[76px]" />
        </aside>
      )}
    </div>
  )
}
