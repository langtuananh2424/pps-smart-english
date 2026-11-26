import type { AnswerProps } from './answer'
import { SpeakingAnswer } from './SpeakingAnswer'
import { WritingAnswer } from './WritingAnswer'

// Thẻ trả lời của câu Viết/Nói trong lượt (Figma 13A).
export const AiAnswer = (p: AnswerProps) => (p.question.type === 'WRITING' ? <WritingAnswer {...p} /> : <SpeakingAnswer {...p} />)
