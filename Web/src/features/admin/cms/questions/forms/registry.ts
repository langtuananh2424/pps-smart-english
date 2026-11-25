import type { ComponentType } from 'react'
import type { ChoiceDraft, Obj, QuestionType } from '../types'
import { ClozeEditor, WordBankEditor } from './blankForms'
import { ChoiceEditor, FillTextEditor, newChoice, TrueFalseEditor } from './choiceForms'
import { MatchEditor, MatchOptionsEditor } from './matchForms'
import type { EditorProps } from './props'
import { RewriteEditor, SpeakingEditor, WritingEditor } from './textForms'
import { BuildEditor, IdentifyEditor, SpotFixEditor } from './tokenForms'

interface Shape {
  payload: Obj
  answerKey: Obj
  choices: ChoiceDraft[]
}
interface Entry {
  Editor: ComponentType<EditorProps>
  empty: () => Shape
}

const two = () => [newChoice(0), newChoice(1)]
const plain = (payload: Obj = {}, answerKey: Obj = {}): (() => Shape) => () => ({ payload, answerKey, choices: [] })
const blocks = [
  { id: 'b1', text: '' },
  { id: 'b2', text: '' },
]

// Mỗi dạng: biểu mẫu riêng và dữ liệu rỗng ban đầu (payload/answerKey đúng dạng backend).
export const REGISTRY: Record<QuestionType, Entry> = {
  CONNECTIVE_MCQ: { Editor: ChoiceEditor, empty: () => ({ payload: {}, answerKey: {}, choices: two() }) },
  CHOICE_MULTI: { Editor: ChoiceEditor, empty: () => ({ payload: { scoring: 'PARTIAL' }, answerKey: {}, choices: two() }) },
  TRUE_FALSE: { Editor: TrueFalseEditor, empty: plain({ options: ['TRUE', 'FALSE'] }, { answer: 'TRUE' }) },
  FILL_TEXT: { Editor: FillTextEditor, empty: plain({}, { accepted: [] }) },
  WORD_BANK: { Editor: WordBankEditor, empty: plain({ text: '', blanks: [], words: [], allowReuse: false, scoring: 'PARTIAL' }, { blanks: {} }) },
  MATCH_OPTIONS: {
    Editor: MatchOptionsEditor,
    empty: plain(
      { items: [{ id: 'i1', text: '' }], options: [{ id: 'o1', text: '' }, { id: 'o2', text: '' }], scoring: 'PARTIAL' },
      { matches: {} },
    ),
  },
  CONNECTIVE_MATCH: {
    Editor: MatchEditor,
    empty: plain({ left: [{ id: 'l1', text: '' }], right: [{ id: 'r1', text: '' }] }, { pairs: {} }),
  },
  CONNECTIVE_CLOZE: { Editor: ClozeEditor, empty: plain({ text: '', blanks: [] }, { blanks: {} }) },
  CONNECTIVE_REWRITE: { Editor: RewriteEditor, empty: plain({ sentence: '', requiredWord: '' }) },
  GRAMMAR_IDENTIFY: { Editor: IdentifyEditor, empty: plain({ tokens: [] }, { spans: [] }) },
  GRAMMAR_SPOT_FIX: { Editor: SpotFixEditor, empty: plain({ tokens: [] }, { corrections: [] }) },
  GRAMMAR_BUILD: { Editor: BuildEditor, empty: plain({ blocks }, { order: ['b1', 'b2'] }) },
  GRAMMAR_REWRITE: { Editor: RewriteEditor, empty: plain({ sentence: '', requiredElement: '' }) },
  WRITING: { Editor: WritingEditor, empty: plain({ task: '', requiredWords: 150, ieltsTask: 'TASK2' }) },
  SPEAKING: { Editor: SpeakingEditor, empty: plain({ part: 1, prompt: '' }) },
}
