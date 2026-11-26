import { useState } from 'react'
import { GroupPassage } from '../practice/GroupPassage'
import { GroupAudio } from '../practice/listening/GroupAudio'
import type { Bank, BankGroup } from '../practice/types'

interface Props { bank: Bank; group: BankGroup; attemptId: number; disabled: boolean; exam: boolean }

// Cột đề (Figma 01.01): bài đọc; audio (Listening) luôn hiện. Mobile: bài đọc mở toàn màn qua nút (Figma 275:798).
export function PassagePane({ bank, group, attemptId, disabled, exam }: Props) {
  // D-151: lượt Mô phỏng thi trên bộ FREE nghe theo luật thi (máy chủ đếm lượt)
  const playback = bank.playbackMode ?? 'FREE'
  const [open, setOpen] = useState(false)
  const hasText = !!group.passageHtml || !!group.title
  return (
    <div className="flex flex-col gap-3">
      {group.hasAudio && <GroupAudio key={group.id} attemptId={attemptId} group={group} mode={exam && playback === 'FREE' ? 'EXAM' : playback} defaultLimit={bank.playLimit} disabled={disabled} />}
      {hasText && (
        <>
          <button type="button" onClick={() => setOpen(true)} className="min-h-11 rounded-full border-[1.5px] border-line-input bg-surface px-4 text-sm font-extrabold text-ink md:hidden">
            Xem bài đọc
          </button>
          <div className={open ? 'fixed inset-0 z-20 overflow-y-auto bg-page p-4' : 'hidden md:block'}>
            {open && (
              <div className="mb-3 flex items-center justify-between">
                <h2 className="text-xl font-black text-ink">Bài đọc</h2>
                <button type="button" aria-label="Đóng bài đọc" onClick={() => setOpen(false)} className="grid size-11 place-items-center rounded-[12px] border border-line bg-surface font-extrabold text-brand-strong">×</button>
              </div>
            )}
            <GroupPassage group={group} />
          </div>
        </>
      )}
    </div>
  )
}
