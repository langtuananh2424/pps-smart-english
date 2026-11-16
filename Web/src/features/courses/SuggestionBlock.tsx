import { Link } from 'react-router'
import banner from '../../assets/courses/banner-ielts.webp'
import chipCheck from '../../assets/courses/chip-check.svg'
import redo from '../../assets/courses/redo.svg'
import { BlockError, Skeleton } from '../home/BlockState'
import { GOAL_LABEL } from '../qa/content'
import type { QaAnswers } from '../qa/schema'
import { bandRange, formatBand } from './format'
import type { Suggestions } from './types'
import type { Trial } from './useTrial'

const chip = 'flex items-center gap-[5px] rounded-full bg-surface/90 px-3 py-[5px] text-xs font-extrabold text-brand-ink lg:bg-surface lg:py-1.5 lg:text-sm lg:font-semibold lg:text-brand-strong'
const Chip = ({ children }: { children: React.ReactNode }) => (
  <li className={chip}>
    <img src={chipCheck} alt="" className="size-3 lg:hidden" />
    {children}
  </li>
)

export function QaInvite() {
  return (
    <Link
      to="/goi-y-khoa"
      className="flex min-h-14 items-center justify-between gap-3 rounded-lg border border-line-subtle bg-subtle px-5 py-3 font-extrabold text-brand-ink"
    >
      <span>Trả lời 4 câu để Cam gợi ý khóa</span>
      <span aria-hidden="true">→</span>
    </Link>
  )
}

interface Props {
  answers: QaAnswers
  suggestions?: Suggestions
  error: boolean
  onRetry: () => void
  trial: Trial
}

// Khối "Gợi ý dành riêng cho bạn" (D-54, D-56).
export function SuggestionBlock({ answers, suggestions: s, error, onRetry, trial }: Props) {
  const primary = s?.primary
  const to = s && s.targetBand != null ? s.targetBand : answers.targetBand
  const current = s?.currentBand
  return (
    <section aria-label="Gợi ý khóa học" className="relative flex flex-col gap-3 overflow-hidden rounded-lg border border-line bg-[#fff1e3] p-4 lg:block lg:p-0 lg:px-8 lg:py-7">
      <img src={banner} alt="" className="absolute right-0 top-0 h-[120px] w-[135px] max-w-none object-cover lg:h-[246px] lg:w-[620px]" />
      <div aria-hidden="true" className="absolute right-[113px] top-0 h-[120px] w-[61px] bg-gradient-to-r from-[#fff1e3] via-[#fff1e3] via-[28%] to-transparent lg:right-[285px] lg:h-[246px] lg:w-[335px]" />
      <div className="relative flex flex-col gap-3 lg:flex-row lg:items-center lg:gap-8">
        <div className="flex flex-1 flex-col items-start gap-3 lg:gap-3.5">
          <div className="flex w-full items-center justify-between">
            <span className="rounded-full bg-surface px-2.5 py-1 text-xs font-black text-brand-ink lg:px-3 lg:font-extrabold">
              <span className="lg:hidden">Gợi ý theo Q&A của bạn</span>
              <span className="hidden lg:inline">Gợi ý dành riêng cho bạn</span>
            </span>
            <Link to="/goi-y-khoa" className="flex min-h-11 items-center gap-[5px] text-sm font-black text-brand-strong underline lg:hidden">
              <img src={redo} alt="" className="size-[15px]" />
              Làm lại
            </Link>
          </div>
          <h2 className="hidden text-2xl font-black leading-[30px] text-ink lg:block">Dựa trên câu trả lời Q&A của bạn</h2>
          <ul className="flex flex-wrap gap-2 lg:pr-0">
            {answers.goals.map((g) => (
              <Chip key={g}>{GOAL_LABEL[g]}</Chip>
            ))}
            {current != null && (
              <Chip>{s?.basis === 'PLACEMENT' ? `Kết quả kiểm tra ${formatBand(current)}` : `Hiện tại ~${formatBand(current)}`}</Chip>
            )}
            <Chip>Mục tiêu {formatBand(to)}</Chip>
          </ul>
          <Link to="/goi-y-khoa" className="hidden min-h-11 items-center text-sm font-semibold text-brand-strong underline lg:flex">
            Làm lại Q&A
          </Link>
        </div>
        <div className="flex w-full flex-col gap-2 rounded-[22px] bg-surface p-3 lg:w-[400px] lg:gap-2.5 lg:rounded-lg lg:px-6 lg:py-5">
          {error ? (
            <BlockError onRetry={onRetry} />
          ) : !s ? (
            <Skeleton className="h-40 w-full" />
          ) : primary ? (
            <>
              <span className="self-start rounded-full bg-success-bg px-2.5 py-[3px] text-xs font-extrabold text-success-fg">Phù hợp nhất</span>
              <h3 className="text-lg font-black leading-6 text-ink lg:text-2xl lg:leading-[30px]">{primary.title}</h3>
              <p className="text-sm font-semibold leading-5 text-ink-secondary">
                {current != null && s.targetBand != null
                  ? `Từ ~${formatBand(current)} lên ${formatBand(s.targetBand)}, đúng mục tiêu của bạn.`
                  : `Dải band của khóa: ${bandRange(primary.bandFrom, primary.bandTo)}`}
              </p>
              <button
                type="button"
                onClick={() => trial.open(primary.slug)}
                disabled={trial.busy === primary.slug}
                className="min-h-[46px] rounded-md bg-brand px-5 py-3 text-base font-black text-white shadow-edge-primary disabled:opacity-60 lg:min-h-12 lg:text-lg"
              >
                Học thử miễn phí →
              </button>
              <Link to={`/khoa-hoc/${primary.slug}`} className="flex min-h-12 items-center justify-center rounded-sm bg-brand-strong px-4 text-[15px] font-extrabold text-white">
                Mua gói học
              </Link>
              {s.path.length > 0 && (
                <p className="text-sm font-semibold text-ink-secondary">Lộ trình tiếp theo: {s.path.map((p) => p.title).join(' → ')}</p>
              )}
            </>
          ) : (
            <>
              <p className="font-bold text-ink">Chưa có khóa phù hợp, hãy làm kiểm tra đầu vào</p>
              <Link to="/kiem-tra-dau-vao" className="flex min-h-12 items-center justify-center rounded-md bg-brand px-5 text-lg font-black text-white shadow-edge-primary">
                Kiểm tra đầu vào
              </Link>
            </>
          )}
          {trial.failed && (
            <p role="alert" className="text-sm font-bold text-danger">
              Không mở được bài học thử. Vui lòng thử lại.
            </p>
          )}
        </div>
      </div>
    </section>
  )
}
