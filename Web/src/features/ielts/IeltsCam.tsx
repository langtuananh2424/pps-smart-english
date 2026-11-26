import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import cam from '../../assets/cam-chao.png'
import { Modal } from '../practice/Modal'
import { pillNeutral } from '../practice/questions/ui'
import { fetchHints } from './api'

// Cam gợi ý khi Luyện tập (FR-AI-06, Figma 07-09): bong bóng gợi ý soạn sẵn theo câu đang làm; gợi ý dài mở popup; thu gọn được.
// Chỉ dựng ở lượt Luyện tập: lượt Mô phỏng thi không gọi và server từ chối (D-151).
// D-150: kiểm tra sai thì Session đổi openKey để Cam tự mở, notice hiện trên danh sách gợi ý (kể cả câu không có gợi ý).
interface Props { attemptId: number; questionId: number; hidden: boolean; notice?: string | null; openKey?: number }

export function IeltsCam({ attemptId, questionId, hidden, notice, openKey }: Props) {
  const [collapsed, setCollapsed] = useState(false)
  const [popup, setPopup] = useState(false)
  const q = useQuery({ queryKey: ['attempt', attemptId, 'hints'], queryFn: () => fetchHints(attemptId), retry: false, staleTime: Infinity })
  useEffect(() => {
    if (openKey) setCollapsed(false)
  }, [openKey])
  const hints = q.data?.find((h) => h.questionId === questionId)?.hints ?? []
  if (hidden || (hints.length === 0 && !notice)) return null
  const detail = hints.filter((h) => h.detail)
  return (
    <aside aria-label="Cam gợi ý" className="fixed bottom-[88px] right-3 z-10 flex max-w-[calc(100vw-1.5rem)] flex-col items-end gap-2 md:right-7">
      {!collapsed && (
        <section aria-label="Gợi ý của Cam" className="w-[282px] rounded-[20px] border border-line bg-surface p-3.5 shadow-edge-card md:w-[326px]">
          <div className="flex items-center justify-between">
            <h2 className="text-[15px] font-extrabold text-brand-strong">Gợi ý của Cam</h2>
            <button type="button" aria-label="Thu gọn Cam" onClick={() => setCollapsed(true)} className="grid size-11 place-items-center rounded-[10px] bg-input text-[13px] font-extrabold text-ink">×</button>
          </div>
          {notice && <p role="status" className="mb-2 text-sm font-extrabold text-danger-fg">{notice}</p>}
          <ul className="flex flex-col gap-2 text-sm font-semibold text-ink">
            {hints.map((h) => <li key={h.id}>{h.content}</li>)}
          </ul>
          {detail.length > 0 && <button type="button" onClick={() => setPopup(true)} className="mt-2 min-h-11 text-sm font-extrabold text-brand-strong">Xem chi tiết</button>}
        </section>
      )}
      <button type="button" aria-expanded={!collapsed} aria-label="Hỏi Cam" onClick={() => setCollapsed((v) => !v)} className="grid size-[60px] place-items-center rounded-full border border-line bg-surface p-0.5 md:size-[76px]">
        <img src={cam} alt="" className="size-full object-contain" />
      </button>
      {popup && (
        <Modal title="Gợi ý của Cam" onClose={() => setPopup(false)}>
          <div className="mt-3 flex flex-col gap-3 text-base font-semibold text-ink">
            {detail.map((h) => <p key={h.id}>{h.detail}</p>)}
          </div>
          <button type="button" onClick={() => setPopup(false)} className={`${pillNeutral} mt-5`}>Đóng</button>
        </Modal>
      )}
    </aside>
  )
}
