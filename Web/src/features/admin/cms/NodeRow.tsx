import { useState, type ReactNode } from 'react'
import { WriteError, btnOutline } from '../ui'
import { cmsWrites, useCmsWrite, type Kind, type Status } from './api'
import { StatusPill } from './ui'

export const small = `${btnOutline} !h-11 !px-3`

interface Props {
  kind: Kind
  id: number
  title: string
  status: Status
  meta?: string
  actions?: ReactNode
  editor?: (close: () => void) => ReactNode
  children?: ReactNode
}

// Một nút cây (level/topic/lesson): xuất bản/hủy, sửa, xóa có xác nhận. Lỗi ghi hiện ngay dưới hàng.
export function NodeRow({ kind, id, title, status, meta, actions, editor, children }: Props) {
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const publish = useCmsWrite((p: boolean) => cmsWrites.setPublished(kind, id, p))
  const remove = useCmsWrite(() => cmsWrites.remove(kind, id))
  const failed = publish.isError ? publish : remove
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-col gap-2 rounded-md border border-line-brand bg-surface p-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 flex-col gap-1">
          <span className="break-words font-extrabold text-ink">{title}</span>
          {meta && <span className="text-sm font-semibold text-ink-secondary">{meta}</span>}
          <StatusPill status={status} />
        </div>
        <div className="flex flex-wrap gap-2">
          {actions}
          <button type="button" className={small} disabled={publish.isPending} onClick={() => publish.mutate(status !== 'PUBLISHED')}>
            {status === 'PUBLISHED' ? 'Hủy xuất bản' : 'Xuất bản'}
          </button>
          {editor && (
            <button type="button" className={small} onClick={() => setEditing((e) => !e)}>
              Sửa
            </button>
          )}
          {confirming ? (
            <button type="button" className={`${small} !border-danger !text-danger-fg`} disabled={remove.isPending} onClick={() => remove.mutate(undefined)}>
              Xác nhận xóa
            </button>
          ) : (
            <button type="button" className={small} onClick={() => setConfirming(true)}>
              Xóa
            </button>
          )}
        </div>
      </div>
      {failed.isError && <WriteError error={failed.error} onRetry={() => failed.reset()} />}
      {editing && editor?.(() => setEditing(false))}
      {children}
    </div>
  )
}
