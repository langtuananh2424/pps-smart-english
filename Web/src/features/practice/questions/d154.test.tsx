import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describeAnswer } from '../describe'
import { GroupLayoutView, LayoutHead } from '../layout'
import type { Answer, Answers, GroupLayout, Question } from '../types'
import { isAnswered, rendererOf } from './registry'

const q = (id: number, type: string, payload: unknown): Question => ({ id, type, prompt: 'P', payload })

function mount(question: Question, layout?: GroupLayout, initial?: Answer) {
  const out: (Answer | undefined)[] = []
  const { Component } = rendererOf(question.type)
  let value = initial
  const ui = () => <Component question={question} layout={layout} value={value} onChange={(v) => { value = v; out.push(v); view.rerender(ui()) }} />
  const view = render(ui())
  return out
}

// Dựng bố cục có state cho nhiều câu.
function mountLayout(layout: GroupLayout, items: Question[]) {
  let answers: Answers = {}
  const ui = () => (
    <GroupLayoutView layout={layout as never} items={items.map((question, i) => ({ question, n: i + 1 }))} answers={answers} onChange={(id, v) => { answers = { ...answers, [id]: v }; view.rerender(ui()) }} />
  )
  const view = render(ui())
  return () => answers
}

describe('D-154 kiểu luyện tập', () => {
  test('LETTER_ORDER: chạm chữ để xếp, chạm lại để gỡ', async () => {
    const out = mount(q(1, 'LETTER_ORDER', { letters: [{ id: 'a', ch: 'c' }, { id: 'b', ch: 'a' }, { id: 'c', ch: 't' }] }))
    await userEvent.click(screen.getByRole('button', { name: 'Chữ c' }))
    await userEvent.click(screen.getByRole('button', { name: 'Chữ a' }))
    expect(out.at(-1)).toEqual({ order: ['a', 'b'] })
    await userEvent.click(screen.getByRole('button', { name: 'Bỏ chữ c ở vị trí 1' }))
    expect(out.at(-1)).toEqual({ order: ['b'] })
    await userEvent.click(screen.getByRole('button', { name: 'Chữ a' }))
    expect(out.at(-1)).toBeUndefined()
    expect(isAnswered('LETTER_ORDER', { order: ['a'] })).toBe(true)
    expect(isAnswered('LETTER_ORDER', undefined)).toBe(false)
  })

  test('DICTATION: ô nhiều dòng trả {text}', async () => {
    const out = mount(q(1, 'DICTATION', { maxWords: 3 }))
    await userEvent.type(screen.getByRole('textbox'), 'a b c d')
    expect(out.at(-1)).toEqual({ text: 'a b c d' })
    expect(screen.getByRole('alert')).toHaveTextContent('Tối đa 3 từ')
  })

  test('LISTEN_FORM đứng riêng và trong phiếu', async () => {
    const form: GroupLayout = { kind: 'FORM', title: 'Booking', rows: [{ label: 'Name', slot: 's1' }, { label: 'City', value: 'Hanoi' }, { label: 'Phone', slot: 's2' }] }
    const get = mountLayout(form, [q(1, 'LISTEN_FORM', { slot: 's1' }), q(2, 'LISTEN_FORM', { slot: 's2' })])
    expect(screen.getByText('Hanoi')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Câu 2'), '0123')
    await userEvent.type(screen.getByLabelText('Câu 1'), 'An')
    expect(get()).toEqual({ 1: { text: 'An' }, 2: { text: '0123' } })
    expect(isAnswered('LISTEN_FORM', { text: ' ' })).toBe(false)
  })
})

describe('D-154 kiểu IELTS', () => {
  test('TFNG và YNNG giữ mã giá trị', async () => {
    const a = mount(q(1, 'IELTS_TFNG', { statement: 's' }))
    await userEvent.click(screen.getByRole('radio', { name: /Not given/ }))
    expect(a.at(-1)).toEqual({ value: 'NOT_GIVEN' })
  })
  test('YNNG', async () => {
    const a = mount(q(1, 'IELTS_YNNG', { statement: 's' }))
    await userEvent.click(screen.getByRole('radio', { name: /Yes/ }))
    expect(a.at(-1)).toEqual({ value: 'YES' })
  })

  test('MATCH_HEADINGS: danh sách ở đầu nhóm, mỗi câu một dropdown trả {option}', async () => {
    const layout: GroupLayout = { kind: 'OPTIONS', title: 'List of headings', options: [{ id: 'i', text: 'Origins' }, { id: 'ii', text: 'Decline' }] }
    render(<LayoutHead layout={layout} />)
    expect(screen.getByRole('heading', { name: 'List of headings' })).toBeInTheDocument()
    const out = mount(q(1, 'IELTS_MATCH_HEADINGS', { paragraph: 'B' }), layout)
    await userEvent.selectOptions(screen.getByLabelText('Chọn đáp án'), 'ii')
    expect(out.at(-1)).toEqual({ option: 'ii' })
    await userEvent.selectOptions(screen.getByLabelText('Chọn đáp án'), '')
    expect(out.at(-1)).toBeUndefined()
  })

  test('MATCH_INFO/FEATURES/ENDINGS/MATCHING/MAP dùng dropdown {option}', async () => {
    for (const [type, layout] of [
      ['IELTS_MATCH_INFO', { kind: 'OPTIONS', options: [{ id: 'A', text: 'x' }] }],
      ['IELTS_MATCH_FEATURES', { kind: 'OPTIONS', options: [{ id: 'A', text: 'x' }] }],
      ['IELTS_MATCH_ENDINGS', { kind: 'OPTIONS', options: [{ id: 'A', text: 'x' }] }],
      ['IELTS_MATCHING', { kind: 'OPTIONS', options: [{ id: 'A', text: 'x' }] }],
      ['IELTS_MAP', { kind: 'MAP', options: [{ id: 'A', x: 1, y: 2 }] }],
    ] as [string, GroupLayout][]) {
      const out = mount(q(1, type, {}), layout)
      await userEvent.selectOptions(screen.getByLabelText('Chọn đáp án'), 'A')
      expect(out.at(-1)).toEqual({ option: 'A' })
      expect(isAnswered(type, { option: 'A' })).toBe(true)
      document.body.innerHTML = ''
    }
  })

  test('SENTENCE và SHORT trả {text}', async () => {
    const s = mount(q(1, 'IELTS_SENTENCE', { before: 'The rain', after: 'in spring.' }))
    await userEvent.type(screen.getByLabelText('Câu trả lời'), 'falls')
    expect(s.at(-1)).toEqual({ text: 'falls' })
    expect(screen.getByText(/The rain/)).toBeInTheDocument()
  })
  test('SHORT', async () => {
    const o = mount(q(1, 'IELTS_SHORT', { question: 'Who?', maxWords: 2 }))
    await userEvent.type(screen.getByRole('textbox'), 'Ann')
    expect(o.at(-1)).toEqual({ text: 'Ann' })
  })

  test('TABLE 2 chỗ trống gắn đúng câu', async () => {
    const layout: GroupLayout = { kind: 'TABLE', title: 'Costs', header: ['Item', 'Price'], rows: [['Bus', '£{p1}'], ['Taxi', '{p2} per day']] }
    const get = mountLayout(layout, [q(11, 'IELTS_TABLE', { slot: 'p1' }), q(12, 'IELTS_TABLE', { slot: 'p2' })])
    expect(screen.getByRole('columnheader', { name: 'Price' })).toBeInTheDocument()
    const price = screen.getByLabelText('Câu 1')
    expect(price.closest('td')).toHaveTextContent('£')
    await userEvent.type(screen.getByLabelText('Câu 2'), '30')
    await userEvent.type(price, '5')
    expect(get()).toEqual({ 11: { text: '5' }, 12: { text: '30' } })
    expect(screen.getByLabelText('Câu 2').closest('td')).toHaveTextContent('per day')
  })

  test('NOTE: ô nhập nằm trong html, thẻ không bị cắt', async () => {
    const layout: GroupLayout = { kind: 'NOTE', title: 'Notes', lines: [{ html: '<p>Opens at <b>{n1}</b> daily</p>' }] }
    const get = mountLayout(layout, [q(1, 'IELTS_NOTE', { slot: 'n1' })])
    const input = screen.getByLabelText('Câu 1')
    expect(input.closest('b')).not.toBeNull()
    await userEvent.type(input, '9am')
    expect(get()[1]).toEqual({ text: '9am' })
  })

  test('FLOW và SUMMARY (TEXT)', async () => {
    mountLayout({ kind: 'FLOW', steps: ['Mix {a}', 'Bake'] }, [q(1, 'IELTS_FLOWCHART', { slot: 'a' })])
    expect(screen.getByLabelText('Câu 1')).toBeInTheDocument()
    expect(screen.getByText('Bake')).toBeInTheDocument()
    document.body.innerHTML = ''
    const get = mountLayout({ kind: 'TEXT', html: '<p>The {x} was big.</p>' }, [q(2, 'IELTS_SUMMARY', { slot: 'x' })])
    await userEvent.type(screen.getByLabelText('Câu 1'), 'dog')
    expect(get()[2]).toEqual({ text: 'dog' })
  })

  test('SUMMARY_BANK: hộp từ một lần, ô chọn từ trả {word}', async () => {
    const layout: GroupLayout = { kind: 'TEXT_BANK', html: '<p>A {w1} day</p>', words: [{ id: 'w-sun', text: 'sunny' }, { id: 'w-rain', text: 'rainy' }] }
    const get = mountLayout(layout, [q(1, 'IELTS_SUMMARY_BANK', { slot: 'w1' })])
    expect(screen.getByRole('list', { name: 'Hộp từ' })).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Câu 1'), 'w-rain')
    expect(get()[1]).toEqual({ word: 'w-rain' })
    expect(isAnswered('IELTS_SUMMARY_BANK', { word: 'w-rain' })).toBe(true)
  })

  test('DIAGRAM: điểm đặt theo % và có số, ô nhập theo câu', async () => {
    const layout: GroupLayout = { kind: 'DIAGRAM', title: 'Engine', image: { url: '/d.png', width: 400, height: 300 }, points: [{ slot: 'd1', x: 25, y: 40 }] }
    const get = mountLayout(layout, [q(1, 'IELTS_DIAGRAM', { slot: 'd1', description: 'The pump' })])
    expect(screen.getByRole('img', { name: 'Engine' })).toHaveAttribute('src', '/d.png')
    const marker = document.querySelector('span[style]') as HTMLElement
    expect(marker.style.left).toBe('25%')
    expect(marker.style.top).toBe('40%')
    expect(screen.getByText('The pump')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Câu 1'), 'valve')
    expect(get()[1]).toEqual({ text: 'valve' })
  })

  test('MAP: bản đồ có điểm A theo %', () => {
    render(<LayoutHead layout={{ kind: 'MAP', title: 'Town', image: { url: '/m.png', width: 10, height: 10 }, options: [{ id: 'A', x: 10, y: 90 }] }} />)
    expect(screen.getByRole('img', { name: 'Town' })).toBeInTheDocument()
    expect((document.querySelector('span[style]') as HTMLElement).style.left).toBe('10%')
  })
})

describe('D-154 màn kết quả', () => {
  const opts: GroupLayout = { kind: 'OPTIONS', options: [{ id: 'ii', text: 'Decline' }] }
  test('hiện chữ của đáp án đúng và câu trả lời', () => {
    expect(describeAnswer(q(1, 'IELTS_MATCH_HEADINGS', {}), { option: 'II' }, undefined, opts)).toBe('ii. Decline')
    expect(describeAnswer(q(1, 'IELTS_SUMMARY_BANK', {}), { word: 'w1' }, undefined, { kind: 'TEXT_BANK', html: '', words: [{ id: 'w1', text: 'sunny' }] })).toBe('sunny')
    expect(describeAnswer(q(1, 'IELTS_TABLE', {}), { accepted: ['30', 'thirty'] })).toBe('30')
    expect(describeAnswer(q(1, 'IELTS_TABLE', {}), { text: 'abc' })).toBe('abc')
    expect(describeAnswer(q(1, 'IELTS_TFNG', {}), { value: 'NOT_GIVEN' })).toBe('Not given')
    expect(describeAnswer(q(1, 'IELTS_MAP', {}), { option: 'B' }, undefined, { kind: 'MAP', options: [{ id: 'B', x: 0, y: 0 }] })).toBe('B')
    expect(describeAnswer(q(1, 'DICTATION', {}), { accepted: ['Hello there'] })).toBe('Hello there')
    const lo = q(1, 'LETTER_ORDER', { letters: [{ id: 'a', ch: 'c' }, { id: 'b', ch: 'a' }, { id: 'c', ch: 't' }] })
    expect(describeAnswer(lo, { word: 'cat' })).toBe('cat')
    expect(describeAnswer(lo, { order: ['a', 'b', 'c'] })).toBe('cat')
  })
})
