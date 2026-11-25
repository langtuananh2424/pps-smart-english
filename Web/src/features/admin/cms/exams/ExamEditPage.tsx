import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { btnPrimary, Loading, PageHead, RetryError, Section } from '../../ui'
import { AttachPanel } from './AttachPanel'
import { examApi, isPlacement, SKILL_LABEL, useBankDetail, useExamWrite, usePlacementConfig, type Bank } from './api'
import { BankBandRules } from './BankBandRules'
import { PublishPanel } from './PublishPanel'
import { ErrorText, Field } from './ui'

const minutesOf = (sec: number | null) => (sec ? String(Math.round((sec / 60) * 100) / 100) : '')

// Sửa tên và thời gian. Thời gian đã đặt thì không xóa về "không giới hạn" được (BE chỉ nhận số >= 1 giây).
function PropsForm({ bank }: { bank: Bank }) {
  const [title, setTitle] = useState(bank.title)
  const [minutes, setMinutes] = useState(minutesOf(bank.timeLimitSec))
  const write = useExamWrite((b: { title?: string; timeLimitSec?: number }) => examApi.patchBank(bank.id, b))
  const mins = Number(minutes)
  const timeOk = minutes === '' ? bank.timeLimitSec === null : mins > 0
  const valid = title.trim() !== '' && timeOk
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (valid) write.mutate({ title: title.trim(), ...(minutes === '' ? {} : { timeLimitSec: Math.round(mins * 60) }) })
  }
  return (
    <Section title="Thuộc tính đề">
      <form onSubmit={submit} className="flex flex-col gap-3">
        <div className="flex flex-col gap-3 md:flex-row">
          <Field label="Tên đề" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
          <Field
            label="Thời gian làm bài (phút)"
            type="number"
            min={1}
            step="any"
            value={minutes}
            onChange={(e) => setMinutes(e.target.value)}
            hint={bank.timeLimitSec ? 'Đã đặt giới hạn thì chỉ sửa được, không bỏ giới hạn.' : 'Để trống = không giới hạn.'}
          />
        </div>
        <ErrorText error={write.error} />
        {write.isSuccess && <p role="status" className="font-bold text-brand-strong">Đã lưu.</p>}
        <button type="submit" disabled={!valid || write.isPending} className={`${btnPrimary} self-start`}>
          Lưu thuộc tính
        </button>
      </form>
    </Section>
  )
}

function RetakeSummary() {
  const q = usePlacementConfig()
  if (!q.data) return null
  return (
    <Section title="Cấu hình đề đầu vào">
      <p className="font-semibold text-ink-secondary">
        Số lần làm lại tối đa: {q.data.maxRetakes === null ? 'không cho làm lại' : q.data.maxRetakes}.{' '}
        <Link to="/quan-tri/cms/de-thi/quy-doi" className="font-extrabold text-brand-strong underline">
          Sửa bảng quy đổi và số lần làm lại
        </Link>
      </p>
    </Section>
  )
}

// FR-ADM-06 (Figma 482:17010): soạn thuộc tính, xem câu, gắn vào khóa, xuất bản.
export function ExamEditPage() {
  const { bankId = '' } = useParams()
  const q = useBankDetail(bankId)
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />
  const { bank, questions } = q.data
  return (
    <>
      <PageHead title="Soạn đề thi" sub={bank.title} />
      <Link to="/quan-tri/cms/de-thi" className="min-h-11 self-start font-extrabold text-brand-strong underline">
        Quay lại danh sách đề
      </Link>
      <PropsForm key={`${bank.id}-${bank.title}-${bank.timeLimitSec}`} bank={bank} />
      <Section title={`Câu hỏi (${questions.length})`}>
        {questions.length === 0 ? (
          <p className="font-semibold text-ink-secondary">Đề chưa có câu hỏi.</p>
        ) : (
          <ol className="list-decimal pl-6">
            {questions.map((c) => (
              <li key={c.id} className="py-1.5 font-semibold text-ink">
                <span className="break-words">{c.prompt}</span>{' '}
                <span className="text-sm text-ink-secondary">
                  ({SKILL_LABEL[c.skill] ?? c.skill} · {c.type})
                </span>
              </li>
            ))}
          </ol>
        )}
        <Link to={`/quan-tri/cms/ngan-hang/${bank.id}`} className="min-h-11 self-start font-extrabold text-brand-strong underline">
          Soạn câu hỏi của đề
        </Link>
      </Section>
      {isPlacement(bank) ? <RetakeSummary /> : <AttachPanel bank={bank} />}
      {!isPlacement(bank) && <BankBandRules bankId={bank.id} />}
      <PublishPanel detail={q.data} />
    </>
  )
}
