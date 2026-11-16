import type { ReactNode } from 'react'
import { formatBand } from '../courses/format'
import { BANDS, GOALS, HOURS, SELF_LEVELS, SKILLS } from './content'
import { OptionCard } from './OptionCard'
import type { Draft, Goal } from './schema'

interface StepProps {
  draft: Draft
  set: (patch: Draft) => void
  onNotice: (text: string) => void
}

const two = 'grid gap-2.5 lg:grid-cols-2 lg:gap-3.5'
const toggle = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v])

function Group({ label, children, className = two }: { label: string; children: ReactNode; className?: string }) {
  return (
    <div role="group" aria-label={label} className={className}>
      {children}
    </div>
  )
}

export function StepLevel({ draft, set }: StepProps) {
  return (
    <Group label="Trình độ hiện tại">
      {SELF_LEVELS.map((o) => (
        <OptionCard key={o.value} label={o.label} desc={o.desc} selected={draft.selfLevel === o.value} onClick={() => set({ selfLevel: o.value })} />
      ))}
    </Group>
  )
}

export function StepGoals({ draft, set, onNotice }: StepProps) {
  const goals = draft.goals ?? []
  const pick = (g: Goal) => {
    // Chọn mục thứ 3: không cho, nhắc nhẹ.
    if (!goals.includes(g) && goals.length >= 2) return onNotice('Bạn chỉ chọn được tối đa 2 mục. Bỏ một mục để chọn mục khác nhé.')
    onNotice('')
    set({ goals: toggle(goals, g) })
  }
  return (
    <Group label="Mục tiêu học (tối đa 2)" className="grid gap-2.5 lg:grid-cols-3 lg:gap-3.5">
      {GOALS.map((g) => (
        <OptionCard key={g.value} label={g.label} desc={g.desc} icon={g.icon} tile={g.tile} selected={goals.includes(g.value)} onClick={() => pick(g.value)} />
      ))}
    </Group>
  )
}

export function StepBand({ draft, set }: StepProps) {
  return (
    <Group label="Band IELTS mục tiêu" className="grid grid-cols-3 gap-3 sm:grid-cols-5">
      {BANDS.map((b) => (
        <button
          key={b}
          type="button"
          aria-pressed={draft.targetBand === b}
          onClick={() => set({ targetBand: b })}
          className={`min-h-14 rounded-md text-xl font-black ${
            draft.targetBand === b ? 'border-2 border-brand bg-page text-brand-strong' : 'border border-line bg-surface text-ink shadow-edge-card'
          }`}
        >
          {formatBand(b)}
        </button>
      ))}
    </Group>
  )
}

export function StepTime({ draft, set }: StepProps) {
  const skills = draft.skills ?? []
  return (
    <div className="flex flex-col gap-6">
      <Group label="Thời gian học mỗi tuần">
        {HOURS.map((o) => (
          <OptionCard key={o.value} label={o.label} desc={o.desc} selected={draft.weeklyHours === o.value} onClick={() => set({ weeklyHours: o.value })} />
        ))}
      </Group>
      <div>
        <h2 className="mb-1 text-lg font-black text-ink">Kỹ năng muốn cải thiện</h2>
        <p className="mb-3 text-sm font-semibold text-ink-secondary">Chọn nhiều tùy ý, không bắt buộc.</p>
        <Group label="Kỹ năng muốn cải thiện">
          {SKILLS.map((o) => (
            <OptionCard key={o.value} label={o.label} desc={o.desc} selected={skills.includes(o.value)} onClick={() => set({ skills: toggle(skills, o.value) })} />
          ))}
        </Group>
      </div>
    </div>
  )
}
