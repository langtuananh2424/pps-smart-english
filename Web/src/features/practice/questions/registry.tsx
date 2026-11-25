import type { ComponentType } from 'react'
import type { Answer, QuestionProps } from '../types'
import { Build, buildAnswered } from './Build'
import { ChoiceMulti, choiceMultiAnswered } from './ChoiceMulti'
import { Cloze, clozeAnswered } from './Cloze'
import { FillText, fillTextAnswered } from './FillText'
import { Identify, identifyAnswered } from './Identify'
import { Match, matchAnswered } from './Match'
import { MatchOptions, matchOptionsAnswered } from './MatchOptions'
import { Mcq, mcqAnswered } from './Mcq'
import { Rewrite, rewriteAnswered } from './Rewrite'
import { SpotFix, spotFixAnswered } from './SpotFix'
import { TrueFalse, trueFalseAnswered } from './TrueFalse'
import { Unsupported } from './Unsupported'
import { WordBank, wordBankAnswered } from './WordBank'

interface Renderer {
  Component: ComponentType<QuestionProps>
  answered: (v: Answer | undefined) => boolean
}

// Strategy: thêm dạng mới chỉ cần thêm một dòng ở đây.
const renderers: Record<string, Renderer> = {
  CONNECTIVE_MCQ: { Component: Mcq, answered: mcqAnswered },
  CONNECTIVE_MATCH: { Component: Match, answered: matchAnswered },
  CONNECTIVE_CLOZE: { Component: Cloze, answered: clozeAnswered },
  CONNECTIVE_REWRITE: { Component: Rewrite, answered: rewriteAnswered },
  GRAMMAR_IDENTIFY: { Component: Identify, answered: identifyAnswered },
  GRAMMAR_SPOT_FIX: { Component: SpotFix, answered: spotFixAnswered },
  GRAMMAR_BUILD: { Component: Build, answered: buildAnswered },
  GRAMMAR_REWRITE: { Component: Rewrite, answered: rewriteAnswered },
  CHOICE_MULTI: { Component: ChoiceMulti, answered: choiceMultiAnswered },
  TRUE_FALSE: { Component: TrueFalse, answered: trueFalseAnswered },
  FILL_TEXT: { Component: FillText, answered: fillTextAnswered },
  WORD_BANK: { Component: WordBank, answered: wordBankAnswered },
  MATCH_OPTIONS: { Component: MatchOptions, answered: matchOptionsAnswered },
}

export const rendererOf = (type: string): Renderer => renderers[type] ?? { Component: Unsupported, answered: () => false }
// Câu làm ở màn khác (Viết/Nói) không tính vào số câu chưa làm.
export const isAnswerable = (type: string) => type in renderers
export const isAnswered = (type: string, v: Answer | undefined) => rendererOf(type).answered(v)
