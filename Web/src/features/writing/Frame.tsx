import type { ReactNode } from 'react'
import { PlayHeader } from '../practice/PlayHeader'
import type { RootQuestion } from './types'

const noop = () => {}

// Khung màn Viết (Figma 10.26): thanh đầu, hai cột đề | trả lời (xếp dọc trên mobile), thanh thao tác dưới.
export function Frame({ left, children, actions }: { left?: ReactNode; children: ReactNode; actions?: ReactNode }) {
  return (
    <div className="min-h-dvh bg-page pb-[88px]">
      <PlayHeader title="Viết tự luận" secondsLeft={null} draft="idle" canPause={false} paused={false} onPause={noop} />
      <main className={`mx-auto grid max-w-[1384px] items-start gap-5 px-4 py-3 md:px-7 ${left ? 'lg:grid-cols-2' : 'max-w-[900px]'}`}>
        {left}
        <div className="flex flex-col gap-4">{children}</div>
      </main>
      {actions && (
        <nav aria-label="Thao tác" className="fixed inset-x-0 bottom-0 z-20 flex h-[72px] items-center justify-end gap-3 border-t border-line bg-surface px-4 md:px-7">
          {actions}
        </nav>
      )}
    </div>
  )
}

export function PromptCard({ question }: { question: RootQuestion }) {
  const required = question.payload?.requiredWords
  return (
    <section aria-label="Đề viết" className="flex flex-col gap-3 rounded-[20px] border border-line bg-surface p-[22px]">
      <p className="text-[13px] font-extrabold leading-[18px] text-brand-strong">Đề viết</p>
      <p className="text-2xl font-black leading-[30px] text-ink">{question.prompt}</p>
      {question.payload?.task && <p className="text-base font-semibold text-ink-secondary">{question.payload.task}</p>}
      {required != null && <p className="text-[13px] font-extrabold text-ink-secondary">Yêu cầu tối thiểu {required} từ</p>}
    </section>
  )
}

export const card = 'flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6'
export const area =
  'min-h-[240px] w-full resize-y rounded-md border-[1.5px] border-line-input bg-input p-3.5 text-base font-semibold text-ink outline-none placeholder:text-ink-secondary focus:border-brand disabled:opacity-60'
