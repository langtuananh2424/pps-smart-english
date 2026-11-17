import { useState } from 'react'
import { Link } from 'react-router'
import arrowLeft from '../../assets/qa/arrow-left.svg'
import searchIcon from '../../assets/lectures/search.svg'
import { btnNeutral } from '../lectures/ui'
import { percent } from './format'

interface Props {
  courseTitle: string
  answered: number | null
  accuracy: number | null
  query: string
  onQuery: (q: string) => void
}

// Desktop: breadcrumb, tiêu đề, chip số câu, đổi khóa, ô tìm. Mobile: nút quay lại + nút tìm bật/tắt ô tìm.
export function Header({ courseTitle, answered, accuracy, query, onQuery }: Props) {
  const [searching, setSearching] = useState(false)
  const search = (
    <label className={`min-h-12 items-center gap-2.5 rounded-md border-2 border-line bg-surface px-4 lg:flex lg:w-[300px] ${searching ? 'flex' : 'hidden'}`}>
      <img src={searchIcon} alt="" width={20} height={20} />
      <input
        type="search"
        value={query}
        onChange={(e) => onQuery(e.target.value)}
        placeholder="Tìm dạng bài…"
        aria-label="Tìm dạng bài hoặc bài giảng"
        className="min-w-0 flex-1 bg-transparent font-bold text-ink outline-none placeholder:text-[#757575]"
      />
    </label>
  )
  return (
    <header className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
      <div className="flex items-center gap-2.5 lg:flex-col lg:items-start lg:gap-1.5">
        <Link to="/bai-tap" aria-label="Về danh sách khóa học" className="grid size-[46px] shrink-0 place-items-center rounded-sm border-2 border-line bg-surface lg:hidden">
          <img src={arrowLeft} alt="" width={20} height={20} />
        </Link>
        <nav aria-label="Đường dẫn" className="hidden items-center gap-1.5 text-sm font-bold text-ink-muted lg:flex">
          <Link to="/bai-tap">Bài tập</Link>
          <span aria-hidden="true">/</span>
          <span className="font-extrabold text-ink">{courseTitle}</span>
        </nav>
        <div className="min-w-0 flex-1 lg:flex-none">
          <p className="truncate text-xs font-extrabold leading-4 text-ink-muted lg:hidden">{courseTitle}</p>
          <h1 className="text-2xl font-black leading-[30px] text-ink lg:text-[30px] lg:leading-[38px]">
            <span className="lg:hidden">Bài tập</span>
            <span className="max-lg:hidden">Hệ thống bài tập</span>
          </h1>
        </div>
        <button
          type="button"
          aria-label="Tìm kiếm"
          aria-expanded={searching}
          onClick={() => setSearching((v) => !v)}
          className="grid size-11 shrink-0 place-items-center rounded-sm border-2 border-line bg-surface lg:hidden"
        >
          <img src={searchIcon} alt="" width={20} height={20} />
        </button>
      </div>
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
        {answered != null && (
          <span className="hidden self-start rounded-full bg-subtle px-3.5 py-2 text-sm font-semibold text-brand-ink lg:inline-flex">
            {answered} câu đã làm{accuracy != null && ` · đúng ${percent(accuracy)}`}
          </span>
        )}
        <Link to="/bai-tap" className={`${btnNeutral} hidden rounded-md lg:inline-flex`}>
          Đổi khóa học
        </Link>
        {search}
      </div>
    </header>
  )
}
