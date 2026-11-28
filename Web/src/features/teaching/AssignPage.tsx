import { useState } from 'react'
import { useNavigate } from 'react-router'
import cam from '../../assets/cam-chao.png'
import { ApiError } from '../../lib/api'
import { PageHead, Section, WriteError, btnPrimary, inputCls, labelCls } from '../admin/ui'
import { useAssignable, useClassDetail, useCreateAssignment, type AsgMode } from './api'
import { ASG_MODE_LABEL, classPath, KICKER, localToIso } from './format'
import type { Picked } from './pick'
import { PickStep } from './PickStep'
import { Recipients, toggled, type Scope } from './Recipients'
import { Gate, NavLinkBtn, btnOutline, useNum } from './ui'

type Step = 1 | 2 | 3
// Trường lỗi của backend (details) quay về bước chứa nó.
const STEP_OF: Record<string, Step> = { bankId: 1, questionId: 1, dueAt: 2, studentIds: 2, mode: 2 }

// Màn "Chưa thể giao bài" (Figma 480:13755): 409 assignment_duplicate hoặc 400 validation.
function Blocked({ error, onEdit }: { error: ApiError; onEdit: () => void }) {
  const items = error.details && typeof error.details === 'object' ? Object.values(error.details).filter((v): v is string => typeof v === 'string') : []
  return (
    <section role="alert" className="mx-auto mt-4 flex w-full max-w-[420px] flex-col items-center gap-3 rounded-lg border border-line-brand bg-page p-6 text-center shadow-edge-card">
      <img src={cam} alt="" className="size-24 rounded-full bg-danger-bg object-contain" />
      <h2 className="text-2xl font-black text-ink">Chưa thể giao bài</h2>
      <p className="font-semibold text-ink-secondary">{error.message}</p>
      {items.length > 0 && <ul className="list-disc text-left font-semibold text-ink-secondary">{items.map((m) => <li key={m}>{m}</li>)}</ul>}
      <button type="button" onClick={onEdit} className={btnPrimary}>Chỉnh sửa</button>
    </section>
  )
}

// #61, FR-ASG-01: giao bài 3 bước (chọn bài, người nhận và hạn, xác nhận).
export function AssignPage() {
  const classId = useNum('classId')
  const nav = useNavigate()
  const tree = useAssignable(classId)
  const detail = useClassDetail(classId)
  const create = useCreateAssignment(classId)
  const [step, setStep] = useState<Step>(1)
  const [pick, setPick] = useState<Picked | null>(null)
  const [scope, setScope] = useState<Scope>('ALL')
  const [picked, setPicked] = useState(new Set<number>())
  const [date, setDate] = useState('')
  const [time, setTime] = useState('23:59')
  const [chosen, setChosen] = useState<AsgMode>('PRACTICE')
  const [blocked, setBlocked] = useState<ApiError | null>(null)
  const isBank = pick?.bankId != null
  // Bộ thi chỉ giao ở chế độ Thi (backend chặn Luyện tập); bộ luyện tập cho chọn.
  const examBank = pick?.kind === 'EXAM'
  const mode: AsgMode = examBank ? 'EXAM' : chosen
  const members = detail.data?.members ?? []
  const stepOk = step === 1 ? !!pick : !!date && !!time && (scope === 'ALL' || picked.size > 0)

  const submit = () => {
    if (!pick) return
    create.mutate(
      { bankId: pick.bankId, questionId: pick.questionId, dueAt: localToIso(`${date}T${time}`), studentIds: scope === 'ALL' ? [] : [...picked], mode: isBank ? mode : undefined },
      {
        onSuccess: (a) => nav(classPath(classId, `/bai-giao/${a.id}`)),
        onError: (e) => e instanceof ApiError && (e.status === 400 || e.code === 'assignment_duplicate') && setBlocked(e),
      },
    )
  }
  const edit = () => {
    const field = blocked?.details && typeof blocked.details === 'object' ? Object.keys(blocked.details).find((k) => STEP_OF[k]) : undefined
    setStep(field ? STEP_OF[field] : blocked?.code === 'assignment_duplicate' ? 1 : 2)
    setBlocked(null)
    create.reset()
  }
  const due = date && time ? new Date(`${date}T${time}`).toLocaleString('vi-VN') : ''

  return (
    <>
      <PageHead kicker={KICKER} title="Giao bài" sub={blocked ? 'Kiểm tra dữ liệu trước khi giao.' : `Bước ${step}/3 · ${['Chọn bài đã xuất bản trong khóa', 'Người nhận và hạn nộp', 'Kiểm tra trước khi thông báo cho học viên.'][step - 1]}`} />
      {blocked ? (
        <Blocked error={blocked} onEdit={edit} />
      ) : (
        <>
          {step === 1 && <Gate q={tree}>{(t) => <PickStep tree={t} pick={pick} onPick={setPick} />}</Gate>}
          {step === 2 && (
            <Section title="Người nhận và hạn nộp">
              <Recipients members={members} scope={scope} onScope={setScope} selected={picked} onToggle={(id) => setPicked((s) => toggled(s, id))} />
              <div className="grid gap-3 md:grid-cols-2">
                <div className="flex flex-col gap-2">
                  <label htmlFor="due-date" className={labelCls}>Hạn nộp (ngày)</label>
                  <input id="due-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} className={inputCls} />
                </div>
                <div className="flex flex-col gap-2">
                  <label htmlFor="due-time" className={labelCls}>Hạn nộp (giờ)</label>
                  <input id="due-time" type="time" value={time} onChange={(e) => setTime(e.target.value)} className={inputCls} />
                </div>
              </div>
              {isBank && (
                <fieldset className="flex flex-col gap-1">
                  <legend className="text-sm font-extrabold text-ink">Chế độ</legend>
                  {(['PRACTICE', 'EXAM'] as const).map((m) => (
                    <label key={m} className="flex min-h-11 items-center gap-2 font-bold text-ink">
                      <input type="radio" name="mode" checked={mode === m} disabled={examBank && m === 'PRACTICE'} onChange={() => setChosen(m)} className="size-5 accent-brand-strong" />
                      {ASG_MODE_LABEL[m]}
                    </label>
                  ))}
                </fieldset>
              )}
            </Section>
          )}
          {step === 3 && pick && (
            <Section title="Xác nhận giao bài">
              <dl className="grid gap-2 font-semibold text-ink md:grid-cols-2">
                <div><dt className="text-[13px] font-extrabold text-ink-secondary">Bài</dt><dd>{pick.title}</dd></div>
                <div><dt className="text-[13px] font-extrabold text-ink-secondary">Người nhận</dt><dd>{scope === 'ALL' ? 'Cả lớp' : `${picked.size} học viên`}</dd></div>
                <div><dt className="text-[13px] font-extrabold text-ink-secondary">Hạn nộp</dt><dd>{due}</dd></div>
                {isBank && <div><dt className="text-[13px] font-extrabold text-ink-secondary">Chế độ</dt><dd>{ASG_MODE_LABEL[mode]}</dd></div>}
              </dl>
              <div className="rounded-md bg-subtle p-3 font-semibold text-ink-secondary">
                <p className="font-extrabold text-ink">Bài thuộc Unit đang khóa</p>
                <p>Chỉ bài được giao sẽ mở cho người nhận.</p>
              </div>
              {create.isError && !blocked && <WriteError error={create.error} onRetry={submit} />}
            </Section>
          )}
          <div className="flex flex-wrap gap-2">
            {step > 1 && <button type="button" onClick={() => setStep((s) => (s - 1) as Step)} className={btnOutline}>Quay lại</button>}
            {step < 3 ? (
              <button type="button" disabled={!stepOk} onClick={() => setStep((s) => (s + 1) as Step)} className={btnPrimary}>{step === 1 ? 'Tiếp tục' : 'Xem lại'}</button>
            ) : (
              <button type="button" disabled={create.isPending} onClick={submit} className={btnPrimary}>Giao bài</button>
            )}
            <NavLinkBtn to={classPath(classId)}>Hủy</NavLinkBtn>
          </div>
        </>
      )}
    </>
  )
}
