import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describeAnswer } from '../describe'
import type { Answer, Question } from '../types'
import { rendererOf, isAnswered, isAnswerable } from './registry'

const q = (type: string, payload: unknown, extra: Partial<Question> = {}): Question => ({ id: 1, type, prompt: 'P', payload, ...extra })

// Dựng renderer có state, trả mảng các giá trị onChange đã phát ra.
function mount(question: Question, initial?: Answer) {
  const out: (Answer | undefined)[] = []
  const { Component } = rendererOf(question.type)
  let value = initial
  const ui = () => <Component question={question} value={value} onChange={(v) => { value = v; out.push(v); view.rerender(ui()) }} />
  const view = render(ui())
  return out
}

const choices = [{ id: 5, label: 'A', content: 'One' }, { id: 6, label: 'B', content: 'Two' }, { id: 7, label: 'C', content: 'Three' }]

describe('5 kiểu chấm mới', () => {
  test('đều đăng ký', () => {
    for (const t of ['CHOICE_MULTI', 'TRUE_FALSE', 'FILL_TEXT', 'WORD_BANK', 'MATCH_OPTIONS']) expect(isAnswerable(t)).toBe(true)
  })

  test('CHOICE_MULTI bật/tắt, tắt hết thì undefined', async () => {
    const out = mount(q('CHOICE_MULTI', null, { choices }))
    await userEvent.click(screen.getByRole('checkbox', { name: /One/ }))
    await userEvent.click(screen.getByRole('checkbox', { name: /Three/ }))
    expect(out.at(-1)).toEqual({ choiceIds: [5, 7] })
    await userEvent.click(screen.getByRole('checkbox', { name: /One/ }))
    await userEvent.click(screen.getByRole('checkbox', { name: /Three/ }))
    expect(out.at(-1)).toBeUndefined()
    expect(isAnswered('CHOICE_MULTI', { choiceIds: [] })).toBe(false)
    expect(isAnswered('CHOICE_MULTI', { choiceIds: [5] })).toBe(true)
  })

  test('TRUE_FALSE giữ nguyên mã option', async () => {
    const out = mount(q('TRUE_FALSE', { options: ['TRUE', 'FALSE', 'NOT_GIVEN'] }))
    await userEvent.click(screen.getByRole('radio', { name: /Not given/ }))
    expect(out.at(-1)).toEqual({ value: 'NOT_GIVEN' })
    expect(isAnswered('TRUE_FALSE', undefined)).toBe(false)
    expect(isAnswered('TRUE_FALSE', { value: 'YES' })).toBe(true)
  })

  test('FILL_TEXT: nhập, rỗng thì undefined, cảnh báo vượt maxWords', async () => {
    const out = mount(q('FILL_TEXT', { maxWords: 2 }))
    const input = screen.getByLabelText(/Câu trả lời của bạn/)
    expect(screen.getByText(/Tối đa 2 từ/)).toBeInTheDocument()
    await userEvent.type(input, 'a b c')
    expect(out.at(-1)).toEqual({ text: 'a b c' })
    expect(screen.getByRole('alert')).toBeInTheDocument()
    await userEvent.clear(input)
    expect(out.at(-1)).toBeUndefined()
    expect(isAnswered('FILL_TEXT', { text: '  ' })).toBe(false)
  })

  test('WORD_BANK khóa từ đã dùng khi không cho trùng', async () => {
    const payload = { text: 'A {1}. B {2}.', blanks: ['1', '2'], words: ['because', 'however'], allowReuse: false }
    const out = mount(q('WORD_BANK', payload))
    await userEvent.selectOptions(screen.getByLabelText(/1\. A/), 'because')
    expect(out.at(-1)).toEqual({ blanks: { 1: 'because' } })
    const second = screen.getByLabelText(/2\. B/)
    expect(second.querySelector('option[value="because"]')).toBeDisabled()
    expect(second.querySelector('option[value="however"]')).toBeEnabled()
    await userEvent.selectOptions(screen.getByLabelText(/1\. A/), '')
    expect(out.at(-1)).toBeUndefined()
  })

  test('WORD_BANK allowReuse cho trùng từ', async () => {
    mount(q('WORD_BANK', { text: 'A {1}. B {2}.', blanks: ['1', '2'], words: ['so'], allowReuse: true }))
    await userEvent.selectOptions(screen.getByLabelText(/1\. A/), 'so')
    expect(screen.getByLabelText(/2\. B/).querySelector('option[value="so"]')).toBeEnabled()
    expect(isAnswered('WORD_BANK', { blanks: { 1: '' } })).toBe(false)
  })

  test('MATCH_OPTIONS chọn và bỏ chọn', async () => {
    const payload = { items: [{ id: 'i1', text: 'Para 1' }], options: [{ id: 'o1', text: 'More jobs' }, { id: 'o2', text: 'Less rain' }] }
    const out = mount(q('MATCH_OPTIONS', payload))
    await userEvent.selectOptions(screen.getByLabelText(/1\. Para 1/), 'o2')
    expect(out.at(-1)).toEqual({ matches: { i1: 'o2' } })
    expect(screen.getByRole('list', { name: 'Ngân hàng đáp án' })).toHaveTextContent('More jobs')
    await userEvent.selectOptions(screen.getByLabelText(/1\. Para 1/), '')
    expect(out.at(-1)).toBeUndefined()
    expect(isAnswered('MATCH_OPTIONS', { matches: { i1: 'o1' } })).toBe(true)
  })

  test('disabled khóa tương tác', () => {
    const { Component } = rendererOf('TRUE_FALSE')
    render(<Component question={q('TRUE_FALSE', { options: ['TRUE', 'FALSE'] })} value={undefined} onChange={() => {}} disabled />)
    expect(screen.getByRole('radio', { name: /True/ })).toBeDisabled()
  })
})

describe('describeAnswer cho 5 kiểu mới', () => {
  test('CHOICE_MULTI', () => {
    const question = q('CHOICE_MULTI', null, { choices })
    expect(describeAnswer(question, { choiceIds: [5, 7] })).toBe('A. One; C. Three')
    expect(describeAnswer(question, { choiceIds: [] })).toBeNull()
  })
  test('TRUE_FALSE: bài làm {value}, đáp án {answer}', () => {
    const question = q('TRUE_FALSE', { options: ['YES', 'NO', 'NOT_GIVEN'] })
    expect(describeAnswer(question, { value: 'NOT_GIVEN' })).toBe('Not given')
    expect(describeAnswer(question, { answer: 'YES' })).toBe('Yes')
  })
  test('FILL_TEXT: bài làm {text}, đáp án {accepted}', () => {
    const question = q('FILL_TEXT', null)
    expect(describeAnswer(question, { text: 'cat' })).toBe('cat')
    expect(describeAnswer(question, { accepted: ['cat', 'cats'] })).toBe('cat / cats')
  })
  test('WORD_BANK', () => {
    expect(describeAnswer(q('WORD_BANK', {}), { blanks: { 1: 'because', 2: 'however' } })).toBe('1: because; 2: however')
  })
  test('MATCH_OPTIONS', () => {
    const question = q('MATCH_OPTIONS', { items: [{ id: 'i1', text: 'Para 1' }], options: [{ id: 'o1', text: 'More jobs' }, { id: 'o2', text: 'Less rain' }] })
    expect(describeAnswer(question, { matches: { i1: 'o2' } })).toBe('Para 1 → B. Less rain')
    expect(describeAnswer(question, null)).toBeNull()
  })
})
