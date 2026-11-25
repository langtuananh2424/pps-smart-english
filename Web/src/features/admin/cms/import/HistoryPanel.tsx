import { Loading, RetryError, Section, WriteError, btnOutline } from '../../ui'
import { useImportHistory, useUndoImport } from './api'

const fmt = (iso: string) => new Date(iso).toLocaleString('vi-VN')

// Lịch sử nhập của bộ + hoàn tác theo lô (câu chưa có lượt làm bị xóa, đã có thì ẩn).
export function HistoryPanel({ bankId }: { bankId: string }) {
  const q = useImportHistory(bankId)
  const undo = useUndoImport(bankId)
  return (
    <Section title="Lịch sử nhập">
      {q.isPending && <Loading />}
      {q.isError && <RetryError error={q.error} onRetry={() => q.refetch()} />}
      {q.data?.length === 0 && <p className="font-semibold text-ink-secondary">Chưa có lần nhập nào.</p>}
      {undo.isSuccess && (
        <p role="status" className="font-bold text-ink">
          Đã hoàn tác: xóa {undo.data.deleted} câu, ẩn {undo.data.archived} câu (đã có lượt làm).
        </p>
      )}
      {undo.isError && <WriteError error={undo.error} />}
      <ul className="flex flex-col gap-2">
        {q.data?.map((b) => (
          <li key={b.id} className="flex flex-wrap items-center gap-2 border-b border-line-brand pb-2 last:border-b-0">
            <span className="font-bold text-ink">
              {fmt(b.createdAt)} · {b.source === 'EXCEL' ? 'Excel' : 'Dán nhanh'} · {b.questionCount} câu
            </span>
            {b.undoneAt ? (
              <span className="ml-auto font-extrabold text-ink-secondary">Đã hoàn tác</span>
            ) : (
              <button
                type="button"
                className={`${btnOutline} ml-auto`}
                disabled={undo.isPending}
                onClick={() => undo.mutate(b.id)}
                aria-label={`Hoàn tác lần nhập ${b.id}`}
              >
                Hoàn tác
              </button>
            )}
          </li>
        ))}
      </ul>
    </Section>
  )
}
