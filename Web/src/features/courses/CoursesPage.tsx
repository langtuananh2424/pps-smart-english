import { useMemo, useState } from 'react'
import searchIcon from '../../assets/courses/search.svg'
import { useAuth } from '../../auth/AuthContext'
import { BlockError, Skeleton } from '../home/BlockState'
import { useMyCourses } from '../home/api'
import { useMyQa } from '../qa/api'
import { readLocalQa } from '../qa/storage'
import { useCatalog, useGuestSuggestions, useMySuggestions } from './api'
import { CourseGrid } from './CourseGrid'
import { fold } from './format'
import { QaInvite, SuggestionBlock } from './SuggestionBlock'
import { useTrial } from './useTrial'

type Filter = 'all' | 'learning' | 'trial'
const FILTERS: { id: Filter; label: string }[] = [
  { id: 'all', label: 'Tất cả' },
  { id: 'learning', label: 'Đang học' },
  { id: 'trial', label: 'Học thử' },
]

// Câu trả lời Q&A và gợi ý: học viên lấy từ server, khách từ trình duyệt.
function useQaSuggestion(loggedIn: boolean) {
  const mine = useMyQa(loggedIn)
  const local = useMemo(() => readLocalQa(), [])
  const answers = loggedIn ? mine.data : local
  const mineSugg = useMySuggestions(loggedIn && !!answers)
  const guestSugg = useGuestSuggestions(loggedIn ? null : local)
  const sugg = loggedIn ? mineSugg : guestSugg
  return {
    answers,
    loading: loggedIn && mine.isPending,
    qaError: loggedIn && mine.isError,
    refetchQa: mine.refetch,
    suggestions: sugg.data,
    suggestionsError: sugg.isError,
    refetchSuggestions: sugg.refetch,
  }
}

export function CoursesPage() {
  const { user } = useAuth()
  const trial = useTrial()
  const catalog = useCatalog()
  const mine = useMyCourses(!!user)
  const qa = useQaSuggestion(!!user)
  const [filter, setFilter] = useState<Filter>('all')
  const [query, setQuery] = useState('')
  const [searchOpen, setSearchOpen] = useState(false) // mobile: ô tìm kiếm mở bằng nút kính lúp

  const enrolled = useMemo(() => new Map((mine.data ?? []).map((c) => [c.slug, c])), [mine.data])
  const primarySlug = qa.suggestions?.primary?.slug
  const suggested = useMemo(
    () => new Set([primarySlug, ...(qa.suggestions?.path ?? []).map((p) => p.slug)].filter((s): s is string => !!s)),
    [primarySlug, qa.suggestions],
  )

  const courses = useMemo(() => {
    const q = fold(query.trim())
    const ok = (slug: string, trialCount: number) =>
      filter === 'all' || (filter === 'learning' ? enrolled.get(slug)?.status === 'ACTIVE' : !enrolled.has(slug) && trialCount > 0)
    const list = (catalog.data ?? []).filter((c) => ok(c.slug, c.trialLessonCount) && (!q || fold(`${c.title} ${c.description}`).includes(q)))
    // Khóa gợi ý chính lên đầu (thẻ lớn thứ nhất).
    return [...list.filter((c) => c.slug === primarySlug), ...list.filter((c) => c.slug !== primarySlug)]
  }, [catalog.data, enrolled, filter, query, primarySlug])

  const chips = user ? FILTERS : FILTERS.filter((f) => f.id !== 'learning')

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-center justify-between gap-3 lg:flex-nowrap lg:gap-4">
        <div>
          <h1 className="text-2xl font-black leading-[30px] text-ink lg:text-[30px] lg:leading-[38px]">Khóa học</h1>
          <p className="hidden font-semibold text-ink-secondary lg:block">Chọn khóa phù hợp và đăng ký, học theo lộ trình của riêng bạn.</p>
        </div>
        <button
          type="button"
          aria-label="Tìm khóa học"
          aria-expanded={searchOpen}
          onClick={() => setSearchOpen((o) => !o)}
          className="grid size-11 place-items-center rounded-sm border-2 border-line bg-surface lg:hidden"
        >
          <img src={searchIcon} alt="" className="size-5" />
        </button>
        <input
          type="search"
          aria-label="Tìm khóa học"
          placeholder="Tìm khóa học..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className={`min-h-11 w-full rounded-md border-2 border-line-input bg-surface px-4 py-3 text-sm font-semibold text-ink placeholder:text-ink-muted lg:block lg:w-[300px] ${searchOpen ? 'block' : 'hidden'}`}
        />
      </header>

      {qa.loading ? (
        <Skeleton className="h-60 w-full" />
      ) : qa.qaError ? (
        <BlockError onRetry={() => void qa.refetchQa()} />
      ) : qa.answers ? (
        <SuggestionBlock
          answers={qa.answers}
          suggestions={qa.suggestions}
          error={qa.suggestionsError}
          onRetry={() => void qa.refetchSuggestions()}
          trial={trial}
        />
      ) : (
        <QaInvite />
      )}

      <div role="group" aria-label="Lọc khóa học" className="-mx-4 flex gap-2 overflow-x-auto px-4 lg:mx-0 lg:flex-wrap lg:overflow-visible lg:px-0">
        {chips.map((f) => (
          <button
            key={f.id}
            type="button"
            aria-pressed={filter === f.id}
            onClick={() => setFilter(f.id)}
            className={`min-h-11 shrink-0 rounded-full border-2 px-4 py-2 text-sm font-extrabold lg:px-3.5 lg:font-semibold ${
              filter === f.id ? 'border-brand bg-page text-brand-strong' : 'border-line bg-surface text-ink-secondary'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {catalog.isPending ? (
        <div className="grid gap-5 lg:grid-cols-[1.35fr_1fr]">
          <Skeleton className="h-72" />
          <Skeleton className="h-72" />
        </div>
      ) : catalog.isError ? (
        <BlockError onRetry={() => void catalog.refetch()} />
      ) : courses.length === 0 ? (
        <div className="rounded-lg border border-line-subtle bg-subtle px-6 py-10 text-center">
          <p className="text-lg font-black text-ink">Chưa có khóa nào khớp</p>
          <p className="mt-1 font-semibold text-ink-secondary">Thử đổi từ khóa hoặc bộ lọc nhé.</p>
          <button
            type="button"
            onClick={() => {
              setFilter('all')
              setQuery('')
            }}
            className="mt-3 min-h-11 font-extrabold text-brand-strong underline"
          >
            Xem tất cả khóa
          </button>
        </div>
      ) : (
        <CourseGrid courses={courses} enrolled={enrolled} suggested={suggested} trial={trial} />
      )}
      {trial.failed && !qa.answers && (
        <p role="alert" className="text-sm font-bold text-danger">
          Không mở được bài học thử. Vui lòng thử lại.
        </p>
      )}
    </div>
  )
}
