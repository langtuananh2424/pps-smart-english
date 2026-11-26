import { render, screen } from '@testing-library/react'
import { QuestionsPane } from '../ielts/QuestionsPane'
import { QuestionStem } from './QuestionStem'
import { Mcq } from './questions/Mcq'
import type { Question, QuestionMedia } from './types'

// Tester FR-EX-04 (Task 2a): biên của promptHtml/media; HTML chỉ được dựng từ trường *Html đã làm sạch ở máy chủ.
const q = (extra: Partial<Question> = {}): Question => ({ id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Plain prompt', payload: {}, ...extra })
const img = (extra: Partial<QuestionMedia> = {}): QuestionMedia => ({ assetId: 7, url: 'http://img/7', width: 10, height: 10, role: 'INLINE', displaySize: 'MD', altText: 'Ảnh 7', ...extra })
const pane = (question: Question) =>
  render(<QuestionsPane part={{ first: 0, questions: [question], group: null } as never} answers={{}} flagged={new Set()} currentId={1} disabled={false} onChange={() => {}} onFocus={() => {}} />)

describe('QuestionStem: biên', () => {
  test('promptHtml rỗng hoặc null thì hiện prompt chữ', () => {
    const { rerender } = render(<QuestionStem question={q({ promptHtml: '' })} className="x" />)
    expect(screen.getByText('Plain prompt')).toBeInTheDocument()
    rerender(<QuestionStem question={q({ promptHtml: null })} className="x" />)
    expect(screen.getByText('Plain prompt')).toBeInTheDocument()
  })

  test('IELTS: không prompt, không promptHtml thì hiện hướng dẫn thay thế, không vỡ', () => {
    pane(q({ prompt: '', promptHtml: null, instruction: 'Choose ONE letter' }))
    expect(screen.getByText('Choose ONE letter')).toBeInTheDocument()
  })

  test('IELTS: có promptHtml thì không hiện thêm dòng hướng dẫn thay thế', () => {
    pane(q({ prompt: '', promptHtml: '<p>Rich</p>', instruction: 'Choose ONE letter' }))
    expect(screen.queryByText('Choose ONE letter')).toBeNull()
  })

  test('ảnh INLINE không có trong promptHtml (hoặc không có promptHtml) vẫn hiện, không mất ảnh', () => {
    const { rerender } = render(<QuestionStem question={q({ media: [img()] })} className="x" />)
    expect(screen.getByRole('img', { name: 'Ảnh 7' })).toBeInTheDocument()
    rerender(<QuestionStem question={q({ promptHtml: '<p>x<img data-asset-id="17" src="http://img/17" alt="Ảnh 17"></p>', media: [img()] })} className="x" />)
    expect(screen.getByRole('img', { name: 'Ảnh 7' })).toBeInTheDocument()
  })

  test('ảnh INLINE đã nằm trong promptHtml chỉ hiện một lần; DIAGRAM cùng asset vẫn hiện riêng', () => {
    const html = '<p>x<img data-asset-id="7" src="http://img/7" alt="Ảnh 7"></p>'
    const { rerender } = render(<QuestionStem question={q({ promptHtml: html, media: [img()] })} className="x" />)
    expect(screen.getAllByRole('img', { name: 'Ảnh 7' })).toHaveLength(1)
    rerender(<QuestionStem question={q({ promptHtml: html, media: [img({ assetId: 8, role: 'DIAGRAM', altText: 'Sơ đồ' })] })} className="x" />)
    expect(screen.getByRole('img', { name: 'Sơ đồ' })).toBeInTheDocument()
  })

  test('cỡ ảnh lạ rơi về 50%, cỡ FULL là 100%', () => {
    render(<QuestionStem question={q({ media: [img({ displaySize: 'XXL' as never }), img({ assetId: 9, displaySize: 'FULL', altText: 'Full' })] })} className="x" />)
    expect(screen.getByRole('img', { name: 'Ảnh 7' }).style.width).toBe('50%')
    expect(screen.getByRole('img', { name: 'Full' }).style.width).toBe('100%')
  })

  test('prompt chữ và content lựa chọn có thẻ HTML không thành thẻ', () => {
    const evil = '<img src=x onerror="window.__x=1">'
    const { container } = render(<QuestionStem question={q({ prompt: evil })} className="x" />)
    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText(evil)).toBeInTheDocument()
    const { container: c2 } = render(<Mcq question={q({ choices: [{ id: 1, label: 'A', content: evil, contentHtml: '' }] })} value={undefined} onChange={() => {}} />)
    expect(c2.querySelector('img')).toBeNull()
  })
})
