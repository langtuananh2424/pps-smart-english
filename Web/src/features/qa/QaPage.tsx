import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import arrowRight from '../../assets/icons/arrow-right.svg'
import arrowLeft from '../../assets/qa/arrow-left.svg'
import cam from '../../assets/qa/cam-gioi-thieu.webp'
import { useAuth } from '../../auth/AuthContext'
import { Skeleton } from '../home/BlockState'
import { useMyQa, useSaveQa } from './api'
import { STEP_META } from './content'
import { qaSchema, stepSchemas, type Draft } from './schema'
import { StepBand, StepGoals, StepLevel, StepTime } from './QaSteps'
import { readLocalQa, writeLocalQa } from './storage'

const STEPS = [StepLevel, StepGoals, StepBand, StepTime]
const ghostBtn = 'flex min-h-14 items-center justify-center gap-2 rounded-md border-2 border-line-input bg-surface px-6 text-lg font-extrabold text-ink disabled:opacity-40'

const backBtn = 'grid size-11 place-items-center rounded-sm bg-surface'

function Wizard({ initial }: { initial: Draft }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const save = useSaveQa()
  const [step, setStep] = useState(0)
  const [draft, setDraft] = useState<Draft>({ skills: [], ...initial })
  const [notice, setNotice] = useState('')
  const last = step === STEPS.length - 1
  const valid = stepSchemas[step].safeParse(draft).success
  const Step = STEPS[step]
  const meta = STEP_META[step]

  const next = () => {
    if (!last) return setStep(step + 1)
    const full = qaSchema.safeParse(draft)
    if (!full.success) return
    if (!user) {
      writeLocalQa(full.data)
      return navigate('/khoa-hoc')
    }
    save.mutate(full.data, { onSuccess: () => navigate('/khoa-hoc') })
  }

  const back = () => {
    setNotice('')
    setStep(step - 1)
  }

  return (
    <div className="flex min-h-[calc(100dvh-8rem)] flex-col gap-4 max-lg:pb-24 lg:gap-8">
      <header className="grid grid-cols-[auto_1fr_auto] items-center gap-x-3 gap-y-2.5 lg:grid-cols-[1fr_520px_1fr] lg:gap-y-2">
        {step === 0 ? (
          <Link to="/khoa-hoc" aria-label="Quay lại" className={`${backBtn} lg:hidden`}>
            <img src={arrowLeft} alt="" className="size-5" />
          </Link>
        ) : (
          <button type="button" aria-label="Quay lại" onClick={back} className={`${backBtn} lg:hidden`}>
            <img src={arrowLeft} alt="" className="size-5" />
          </button>
        )}
        <p className="text-center text-sm font-extrabold text-ink-secondary lg:col-start-2 lg:row-start-1">
          Câu {step + 1} / 4<span className="hidden lg:inline"> · Gợi ý khóa học</span>
        </p>
        <Link
          to="/khoa-hoc"
          className="flex min-h-11 items-center justify-self-end text-sm font-extrabold text-ink-secondary lg:col-start-3 lg:row-span-2 lg:row-start-1 lg:self-center lg:text-base"
        >
          Bỏ qua<span className="hidden lg:inline">, xem tất cả khóa</span>
        </Link>
        <div aria-hidden="true" className="col-span-3 flex gap-1.5 lg:col-span-1 lg:col-start-2 lg:row-start-2">
          {STEPS.map((_, i) => (
            <span key={i} className={`h-2 flex-1 rounded-full ${i <= step ? 'bg-gradient-to-r from-brand to-[#ff8a3d]' : 'bg-line-subtle'}`} />
          ))}
        </div>
      </header>

      <div className="flex flex-1 flex-col items-center justify-center gap-3.5 lg:flex-row lg:gap-12">
        <aside className="flex w-full items-end gap-1 lg:w-auto lg:flex-col lg:items-center">
          <p className="rounded-[18px] rounded-bl-[4px] border-2 border-line bg-surface px-4 py-3 text-sm font-black leading-[19px] text-brand-ink shadow-edge-card lg:max-w-[320px] lg:text-lg lg:leading-6">
            <span className="lg:hidden">Hỏi nhanh 4 câu để chọn khóa vừa sức bạn nhé!</span>
            <span className="hidden lg:inline">Mình hỏi nhanh 4 câu để chọn khóa vừa sức bạn nhé!</span>
          </p>
          <img src={cam} alt="Cam, linh vật của PPS" className="-ml-2.5 size-[108px] shrink-0 object-contain max-lg:order-first lg:ml-0 lg:size-[300px]" />
          <p className="hidden text-sm font-bold text-ink-secondary lg:block">Mất khoảng 1 phút</p>
        </aside>

        <section
          aria-labelledby="qa-title"
          className="flex w-full max-w-[880px] flex-1 flex-col gap-3.5 lg:gap-6 lg:rounded-xl lg:border lg:border-line-subtle lg:bg-surface lg:p-9 lg:shadow-edge-card"
        >
          <div className="flex flex-col gap-1 lg:gap-1.5">
            <p className="text-xs font-black uppercase tracking-[0.96px] text-brand-strong lg:text-sm lg:tracking-[1.04px]">
              Câu {step + 1} · {meta.topic}
            </p>
            <h1 id="qa-title" className="text-2xl font-black leading-[30px] text-ink lg:text-[30px] lg:leading-[38px]">
              {meta.title}
            </h1>
            <p className="text-sm font-semibold text-ink-secondary lg:text-base">{meta.desc}</p>
          </div>
          <Step draft={draft} set={(p) => setDraft((d) => ({ ...d, ...p }))} onNotice={setNotice} />
          {notice && (
            <p role="status" className="rounded-sm bg-reward-bg px-4 py-3 text-sm font-bold text-reward-fg">
              {notice}
            </p>
          )}
          {save.isError && (
            <p role="alert" className="rounded-sm bg-danger-bg px-4 py-3 text-sm font-bold text-danger-edge">
              Không lưu được câu trả lời. Vui lòng thử lại.
            </p>
          )}
          {/* Mobile: thanh nút dính đáy, chừa safe-area; desktop: hàng nút cuối thẻ. */}
          <div className="fixed inset-x-0 bottom-0 z-20 flex items-center gap-3 border-t border-line bg-surface px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-3 lg:static lg:z-auto lg:justify-between lg:border-0 lg:bg-transparent lg:p-0 lg:pt-1">
            <button type="button" className={`${ghostBtn} max-lg:hidden`} disabled={step === 0} onClick={back}>
              <img src={arrowLeft} alt="" className="size-5" />
              Quay lại
            </button>
            <span className="hidden text-sm font-bold text-ink-muted lg:block">Xem khóa phù hợp với mục tiêu đã chọn</span>
            <button
              type="button"
              disabled={!valid || save.isPending}
              onClick={next}
              className="flex min-h-14 flex-1 items-center justify-center gap-2 rounded-md bg-brand px-7 text-lg font-black text-white shadow-edge-primary disabled:opacity-50 lg:flex-none"
            >
              {last ? 'Xem gợi ý' : 'Tiếp tục'}
              <img src={arrowRight} alt="" className="size-5" />
            </button>
          </div>
        </section>
      </div>
    </div>
  )
}

// Điền sẵn câu trả lời cũ: từ server nếu đăng nhập, từ localStorage nếu khách.
export function QaPage() {
  const { user } = useAuth()
  const mine = useMyQa(!!user)
  if (user && mine.isPending) return <Skeleton className="h-[480px] w-full" />
  return <Wizard initial={(user ? mine.data : readLocalQa()) ?? {}} />
}
