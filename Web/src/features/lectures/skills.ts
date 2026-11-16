import grammar from '../../assets/lectures/skill-grammar.svg'
import listening from '../../assets/lectures/skill-listening.svg'
import reading from '../../assets/lectures/skill-reading.svg'
import speaking from '../../assets/lectures/skill-speaking.svg'
import writing from '../../assets/lectures/skill-writing.svg'
import type { Skill } from '../home/types'

// Icon (đã tô màu sẵn trong SVG Figma) và nền ô theo kỹ năng.
export const skillMeta: Record<Skill, { icon: string; bg: string }> = {
  LISTENING: { icon: listening, bg: 'bg-[#e8f0ff]' },
  READING: { icon: reading, bg: 'bg-success-bg' },
  WRITING: { icon: writing, bg: 'bg-reward-bg' },
  SPEAKING: { icon: speaking, bg: 'bg-[#fce7f3]' },
  GRAMMAR_VOCAB: { icon: grammar, bg: 'bg-subtle' },
}
// Topic chưa gắn kỹ năng (D-58 cho phép trống).
export const noSkill = { icon: reading, bg: 'bg-muted' }
