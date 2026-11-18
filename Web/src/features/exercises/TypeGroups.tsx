import { useState } from 'react'
import aiQuota from '../../assets/exercises/ai-quota.svg'
import { chip } from '../lectures/ui'
import { skillMeta } from '../lectures/skills'
import type { Quota, Skill } from '../home/types'
import { familiesIn, familyLabel, familyOf, groupTypes, isAiType, labelOf, percent, quotaPeriod, type Family } from './format'
import type { ExerciseStats, TypeStat } from './types'

const familyIcon: Record<Family, Skill> = { CONNECTIVE: 'READING', GRAMMAR: 'GRAMMAR_VOCAB', WRITING: 'WRITING', SPEAKING: 'SPEAKING' }

function TypeCard({ t, weakest }: { t: TypeStat; weakest: boolean }) {
  const meta = skillMeta[familyIcon[familyOf(t.type) ?? 'CONNECTIVE']]
  const ai = isAiType(t.type)
  return (
    <article className="flex items-center gap-3 rounded-md border-2 border-line bg-surface p-3 lg:flex-col lg:items-start lg:gap-2.5 lg:rounded-lg lg:border lg:p-4 lg:shadow-edge-card">
      <div className="flex shrink-0 flex-col gap-2.5 lg:w-full">
        <span className={`grid size-10 place-items-center rounded-sm ${meta.bg}`}>
          <img src={meta.icon} alt="" width={20} height={20} />
        </span>
        <div className="hidden gap-1.5 lg:flex">
          {weakest && <span className={`${chip} bg-brand-soft font-black text-brand-strong`}>Nên luyện</span>}
          {ai && <span className={`${chip} bg-[#eaf1f4] font-black text-[#203c4b]`}>✦ AI chấm</span>}
          <span className={`${chip} bg-muted font-black text-ink-secondary`}>{t.questionCount} câu</span>
        </div>
      </div>
      <div className="min-w-0 flex-1 lg:w-full lg:flex-none">
        <h3 className="text-base font-black leading-[22px] text-ink lg:text-lg lg:leading-6">{labelOf(t.type)}</h3>
        <p className="text-xs font-bold text-ink-secondary lg:hidden">
          {t.questionCount} câu · {ai ? `${t.answered} bài đã nộp` : t.accuracy != null ? `đúng ${percent(t.accuracy)}` : 'Chưa luyện'}
          {weakest && ' · Nên luyện'}
        </p>
      </div>
      <span className={`text-sm font-black lg:hidden ${weakest ? 'text-brand-strong' : 'text-ink'}`}>{ai ? '' : percent(t.accuracy)}</span>
      <div className="hidden w-full flex-col gap-1.5 lg:flex">
        {ai ? (
          <p className="text-sm font-extrabold text-ink-secondary">{t.answered} bài đã nộp</p>
        ) : (
          <>
            <div className="flex justify-between text-sm font-extrabold">
              <span className="text-ink-secondary">Độ chính xác</span>
              <span className={t.accuracy == null ? 'text-ink-muted' : weakest ? 'text-brand-strong' : 'text-ink'}>{t.accuracy == null ? 'Chưa luyện' : percent(t.accuracy)}</span>
            </div>
            <div role="progressbar" aria-label={`Độ chính xác ${labelOf(t.type)}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={t.accuracy ?? 0} className="h-2 overflow-hidden rounded-full bg-muted">
              <div className="h-full rounded-full bg-gradient-to-r from-brand to-[#ff8a3d]" style={{ width: `${t.accuracy ?? 0}%` }} />
            </div>
          </>
        )}
      </div>
    </article>
  )
}

function FilterChip({ on, onClick, children }: { on: boolean; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={onClick}
      className={`min-h-11 shrink-0 rounded-full border-2 px-3.5 text-sm font-extrabold ${on ? 'border-brand bg-[#fff8f1] text-brand-strong' : 'border-line bg-surface text-ink-secondary'}`}
    >
      {children}
    </button>
  )
}

// D-62: nhóm theo họ dạng câu (Từ nối, Ngữ pháp, Viết, Nói); lượt chấm AI của khóa nằm cạnh chip lọc.
export function TypeGroups({ stats, query, quota, courseId }: { stats: ExerciseStats; query: string; quota?: Quota; courseId: number }) {
  const [family, setFamily] = useState<Family | null>(null)
  const present = familiesIn(stats.byType)
  const groups = groupTypes(stats.byType, family, query)
  const q = quota?.courses.find((c) => c.courseId === courseId)
  if (!present.length) return null

  return (
    <section aria-label="Dạng bài" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
        <div className="flex gap-2 overflow-x-auto">
          <FilterChip on={family === null} onClick={() => setFamily(null)}>
            Tất cả
          </FilterChip>
          {present.map((f) => (
            <FilterChip key={f} on={family === f} onClick={() => setFamily(f)}>
              {familyLabel[f]}
            </FilterChip>
          ))}
        </div>
        {q && (
          <p className="flex shrink-0 items-center gap-1.5 text-sm font-extrabold text-[#203c4b]">
            <img src={aiQuota} alt="" width={16} height={16} />
            Lượt chấm AI: {q.remaining}/{q.quota} {quotaPeriod[q.period] ?? ''}
          </p>
        )}
      </div>
      {groups.map((g) => (
        <div key={g.family} className="flex flex-col gap-2.5 lg:gap-3.5">
          <h2 className="text-lg font-black leading-6 text-ink lg:text-2xl lg:leading-[30px]">
            {g.label}
            {(g.family === 'WRITING' || g.family === 'SPEAKING') && <span className="lg:hidden"> · AI chấm</span>}
          </h2>
          <div className="grid gap-2.5 lg:grid-cols-3 lg:gap-4">
            {g.types.map((t) => (
              <TypeCard key={t.type} t={t} weakest={stats.weakest?.type === t.type} />
            ))}
          </div>
        </div>
      ))}
      {!groups.length && <p className="font-bold text-ink-secondary">Không có dạng bài nào khớp.</p>}
    </section>
  )
}
