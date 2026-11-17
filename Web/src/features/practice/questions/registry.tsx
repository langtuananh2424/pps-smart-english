import type { ComponentType } from 'react'
import type { Answer, QuestionProps } from '../types'
import { Build, buildAnswered } from './Build'
import { Cloze, clozeAnswered } from './Cloze'
import { Identify, identifyAnswered } from './Identify'
import { Match, matchAnswered } from './Match'
import { Mcq, mcqAnswered } from './Mcq'
import { Rewrite, rewriteAnswered } from './Rewrite'
import { SpotFix, spotFixAnswered } from './SpotFix'
import { Unsupported } from './Unsupported'

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
}

export const rendererOf = (type: string): Renderer => renderers[type] ?? { Component: Unsupported, answered: () => false }
// Câu làm ở màn khác (Viết/Nói) không tính vào số câu chưa làm.
export const isAnswerable = (type: string) => type in renderers
export const isAnswered = (type: string, v: Answer | undefined) => rendererOf(type).answered(v)
