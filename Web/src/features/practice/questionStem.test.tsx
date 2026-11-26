import { render, screen } from '@testing-library/react'
import { QuestionsPane } from '../ielts/QuestionsPane'
import { QuestionStem } from './QuestionStem'
import { Mcq } from './questions/Mcq'
import type { Question, QuestionMedia } from './types'

const q = (extra: Partial<Question> = {}): Question => ({ id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Plain prompt', payload: {}, ...extra })
const img = (extra: Partial<QuestionMedia> = {}): QuestionMedia => ({ assetId: 7, url: 'http://img/7', width: 10, height: 10, role: 'DIAGRAM', displaySize: 'LG', altText: 'Sơ đồ', ...extra })

describe('QuestionStem', () => {
  test('không có gì mới thì hiện prompt chữ và không có ảnh', () => {
    render(<QuestionStem question={q()} className="x" />)
    expect(screen.getByText('Plain prompt')).toBeInTheDocument()
    expect(screen.queryByRole('img')).toBeNull()
  })
  test('promptHtml render định dạng', () => {
    const { container } = render(<QuestionStem question={q({ promptHtml: '<p>Say <strong>hi</strong></p>' })} className="x" />)
    expect(container.querySelector('strong')?.textContent).toBe('hi')
    expect(screen.queryByText('Plain prompt')).toBeNull()
  })
  test('media hiện img có alt, lazy', () => {
    render(<QuestionStem question={q({ media: [img()] })} className="x" />)
    const el = screen.getByRole('img', { name: 'Sơ đồ' })
    expect(el).toHaveAttribute('loading', 'lazy')
    expect(el).toHaveAttribute('src', 'http://img/7')
  })
  test('ảnh INLINE đã nằm trong promptHtml thì không hiện lại', () => {
    render(<QuestionStem question={q({ promptHtml: '<p><img src="http://img/7" data-asset-id="7" alt=""></p>', media: [img({ role: 'INLINE' })] })} className="x" />)
    expect(screen.queryByRole('img', { name: 'Sơ đồ' })).toBeNull()
  })
})

// Máy chủ đã làm sạch HTML bằng Jsoup (RichText); web chỉ lặp lại cách ChoiceMulti/GroupPassage đang dùng.
test('Mcq hiện contentHtml, thiếu thì dùng content', () => {
  const choices = [{ id: 1, label: 'A', content: 'plain', contentHtml: '<em>fancy</em>' }, { id: 2, label: 'B', content: 'other' }]
  const { container } = render(<Mcq question={q({ choices })} value={undefined} onChange={() => {}} />)
  expect(container.querySelector('em')?.textContent).toBe('fancy')
  expect(screen.getByText('other')).toBeInTheDocument()
})

test('IELTS QuestionsPane hiện promptHtml và ảnh', () => {
  const question = q({ promptHtml: '<p>Look at <u>this</u></p>', media: [img()] })
  const part = { first: 0, questions: [question], group: null } as never
  const { container } = render(<QuestionsPane part={part} answers={{}} flagged={new Set()} currentId={1} disabled={false} onChange={() => {}} onFocus={() => {}} />)
  expect(container.querySelector('u')?.textContent).toBe('this')
  expect(screen.getByRole('img', { name: 'Sơ đồ' })).toBeInTheDocument()
})
