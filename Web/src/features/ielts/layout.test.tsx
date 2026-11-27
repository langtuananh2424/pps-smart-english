import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Answer, Answers, BankGroup, Question } from '../practice/types'
import { QuestionsPane } from './QuestionsPane'
import type { Part } from './types'

const q = (id: number, type: string, payload: unknown): Question => ({ id, type, prompt: 'P' + id, payload })
const group = (layout: BankGroup['layout']): BankGroup => ({ id: 1, title: null, instruction: 'Complete', passageHtml: null, sortOrder: 0, hasAudio: false, audioDurationMs: null, layout })

function mount(part: Part) {
  let answers: Answers = {}
  const focus: number[] = []
  const ui = () => (
    <QuestionsPane part={part} answers={answers} flagged={new Set()} currentId={0} disabled={false} onFocus={(id) => focus.push(id)} onChange={(id, v: Answer | undefined) => { answers = { ...answers, [id]: v }; view.rerender(ui()) }} />
  )
  const view = render(ui())
  return { get: () => answers, focus }
}

describe('khung IELTS với bố cục nhóm (D-154)', () => {
  test('TABLE: số câu tiếp nối từ phần trước, ô gắn đúng câu, bấm vào ô báo câu đang chọn', async () => {
    const part: Part = {
      key: 'g1', first: 4, questions: [q(21, 'IELTS_TABLE', { slot: 'a' }), q(22, 'IELTS_TABLE', { slot: 'b' })],
      group: group({ kind: 'TABLE', header: ['Day', 'Cost'], rows: [['Mon', '{a}'], ['Tue', '{b}']] }),
    }
    const { get, focus } = mount(part)
    expect(screen.getByText('Questions 5–6')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Câu 6'), '12')
    await userEvent.type(screen.getByLabelText('Câu 5'), '7')
    expect(get()).toEqual({ 21: { text: '7' }, 22: { text: '12' } })
    expect(focus).toContain(22)
    expect(document.getElementById('ielts-q-21')).toContainElement(screen.getByLabelText('Câu 5'))
  })

  test('HEADINGS: danh sách hiện một lần, mỗi câu một dropdown', async () => {
    const part: Part = {
      key: 'g1', first: 0, questions: [q(1, 'IELTS_MATCH_HEADINGS', { paragraph: 'A' }), q(2, 'IELTS_MATCH_HEADINGS', { paragraph: 'B' })],
      group: group({ kind: 'OPTIONS', title: 'List of headings', options: [{ id: 'i', text: 'Origins' }, { id: 'ii', text: 'Decline' }], reuse: false }),
    }
    const { get } = mount(part)
    expect(screen.getAllByText('Origins')).toHaveLength(1)
    const [first, second] = screen.getAllByLabelText('Chọn đáp án')
    await userEvent.selectOptions(second, 'ii')
    await userEvent.selectOptions(first, 'i')
    expect(get()).toEqual({ 1: { option: 'i' }, 2: { option: 'ii' } })
  })

  test('nhóm không bố cục: TFNG vẫn là câu thường', async () => {
    const part: Part = { key: 'g1', first: 0, questions: [q(1, 'IELTS_TFNG', { statement: 's' })], group: group(null) }
    const { get } = mount(part)
    await userEvent.click(screen.getByRole('radio', { name: /True/ }))
    expect(get()[1]).toEqual({ value: 'TRUE' })
  })
})
