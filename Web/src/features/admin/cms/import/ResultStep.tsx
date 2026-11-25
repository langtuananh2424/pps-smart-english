import { Link } from 'react-router'
import { btnOutline, btnPrimary, Section } from '../../ui'
import { errorReportCsv, saveBlob, type ImportResult } from './api'

interface Props {
  bankId: string
  result: ImportResult
  onAgain: () => void
}

export function ResultStep({ bankId, result, onAgain }: Props) {
  const report = () => saveBlob(new Blob([errorReportCsv(result.skipped)], { type: 'text/csv;charset=utf-8' }), 'bao-cao-loi-nhap.csv')
  return (
    <Section title="Nhập câu hỏi thành công" tone="brand">
      <p role="status" className="text-lg font-black text-ink">
        Đã thêm {result.imported} câu.
      </p>
      {result.skipped.length > 0 && (
        <>
          <p className="font-extrabold text-ink">Dòng bị bỏ ({result.skipped.length}):</p>
          <ul className="flex flex-col gap-1.5">
            {result.skipped.map((s) => (
              <li key={s.line} className="font-semibold text-ink-secondary">
                Dòng {s.line}: {s.errors.map((e) => e.message).join('; ')}
              </li>
            ))}
          </ul>
        </>
      )}
      <div className="flex flex-wrap gap-2">
        <Link to={`/quan-tri/cms/ngan-hang/${bankId}`} className={btnPrimary}>
          Mở bộ câu hỏi
        </Link>
        {result.skipped.length > 0 && (
          <button type="button" className={btnOutline} onClick={report}>
            Tải báo cáo lỗi (CSV)
          </button>
        )}
        <button type="button" className={btnOutline} onClick={onAgain}>
          Nhập tiếp
        </button>
      </div>
    </Section>
  )
}
