import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { makeQueryClient } from '../../lib/queryClient'
import { json, mockApi } from '../../testUtils'
import { CheckBar } from './CheckBar'
import type { CheckState, GroupLayout, Question } from './types'

// D-163 (tester): đáp án hiện ở CheckBar khi khóa sai, với các dạng tự chấm khác nhau.
const locked = (correctAnswer: CheckState['correctAnswer'], explanation: string | null = 'Giải thích X'): CheckState =>
  ({ questionId: 1, correct: false, checksLeft: 0, wrongCount: 3, correctAnswer, explanation })
const q = (type: string, extra: Partial<Question> = {}): Question => ({ id: 1, type, prompt: 'Đề', payload: {}, ...extra }) as Question

function show(state: CheckState | undefined, question: Question, layout?: GroupLayout | null) {
  mockApi({ '/api/grammar-elements': () => json(200, [{ code: 'SUBJECT', name: 'Chủ ngữ', colorHex: null }]) })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <CheckBar state={state} question={question} layout={layout} canCheck pending={false} onCheck={() => {}} />
    </QueryClientProvider>,
  )
}
const answerLine = () => screen.getByText('Đáp án đúng:').nextSibling?.textContent

describe('CheckBar hiện đáp án khi khóa sai (D-163)', () => {
  test('CHOICE_MULTI: liệt kê mọi lựa chọn đúng', () => {
    show(locked({ choiceIds: [5, 6] }), q('CHOICE_MULTI', { choices: [{ id: 5, label: 'A', content: 'So' }, { id: 6, label: 'B', content: 'But' }, { id: 7, label: 'C', content: 'And' }] }))
    expect(answerLine()).toBe('A. So; B. But')
    expect(screen.getByText('Giải thích X')).toBeInTheDocument()
  })

  test('WORD_BANK', () => {
    show(locked({ blanks: { b1: 'went', b2: 'saw' } }), q('WORD_BANK'))
    expect(answerLine()).toBe('b1: went; b2: saw')
  })

  test('GRAMMAR_IDENTIFY: dùng tên yếu tố từ máy chủ', async () => {
    show(locked({ spans: [{ start: 0, end: 1, element: 'SUBJECT' }] }), q('GRAMMAR_IDENTIFY', { payload: { tokens: ['The', 'cat', 'sleeps'] } }))
    expect(await screen.findByText('The cat (Chủ ngữ)')).toBeInTheDocument()
  })

  test('GRAMMAR_BUILD: ghép khối theo thứ tự đúng', () => {
    show(locked({ order: ['b2', 'b1'] }), q('GRAMMAR_BUILD', { payload: { blocks: [{ id: 'b1', text: 'home' }, { id: 'b2', text: 'Go' }] } }))
    expect(answerLine()).toBe('Go home')
  })

  test('LETTER_ORDER: đáp án là từ', () => {
    show(locked({ word: 'apple' }), q('LETTER_ORDER', { payload: { letters: [{ id: 'x1', ch: 'p' }] } }))
    expect(answerLine()).toBe('apple')
  })

  test('IELTS_MATCH_HEADINGS dùng danh sách lựa chọn của nhóm', () => {
    show(locked({ option: 'ii' }), q('IELTS_MATCH_HEADINGS'), { kind: 'OPTIONS', options: [{ id: 'i', text: 'Một' }, { id: 'ii', text: 'Hai' }] })
    expect(answerLine()).toBe('ii. Hai')
  })

  test('IELTS_SUMMARY_BANK dùng hộp từ của nhóm', () => {
    show(locked({ word: 'w2' }), q('IELTS_SUMMARY_BANK'), { kind: 'TEXT_BANK', html: '', words: [{ id: 'w1', text: 'rain' }, { id: 'w2', text: 'sun' }] })
    expect(answerLine()).toBe('sun')
  })

  test('explanation null: chỉ có dòng đáp án, không có dòng Giải thích', () => {
    show(locked({ choiceId: 5 }, null), q('CONNECTIVE_MCQ', { choices: [{ id: 5, label: 'A', content: 'So' }] }))
    expect(answerLine()).toBe('A. So')
    expect(screen.queryByText('Giải thích:')).not.toBeInTheDocument()
  })

  test('không có đáp án lẫn giải thích: chỉ báo Cần sửa', () => {
    show(locked(null, null), q('CONNECTIVE_MCQ'))
    expect(screen.getByRole('status')).toHaveTextContent('Cần sửa')
    expect(screen.queryByText('Đáp án đúng:')).not.toBeInTheDocument()
  })

  test('giải thích chứa HTML hiển thị như chữ (không chèn mã)', () => {
    show(locked({ choiceId: 5 }, '<img src=x onerror=alert(1)>'), q('CONNECTIVE_MCQ', { choices: [{ id: 5, label: 'A', content: 'So' }] }))
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument()
    expect(document.querySelector('img')).toBeNull()
  })

  test('câu còn lượt hoặc đã đúng: không hiện đáp án dù phản hồi có trường này', () => {
    show({ questionId: 1, correct: false, checksLeft: 1, wrongCount: 2, correctAnswer: { choiceId: 5 }, explanation: 'Lộ' }, q('CONNECTIVE_MCQ', { choices: [{ id: 5, label: 'A', content: 'So' }] }))
    expect(screen.queryByText('Đáp án đúng:')).not.toBeInTheDocument()
    expect(screen.queryByText('Lộ')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Cần sửa, thử lại nhé')
  })
})
