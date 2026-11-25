import type { Details } from '../util'
import type { Draft, GrammarElement } from '../types'

export interface EditorProps {
  draft: Draft
  patch: (p: Partial<Draft>) => void
  errors: Details
  elements: GrammarElement[]
}
