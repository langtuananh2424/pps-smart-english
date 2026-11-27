import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { emptyDraft, toRequest } from '../draft'
import type { BankView, Draft, Obj, QuestionType } from '../types'
import { REGISTRY } from './registry'

const bank = { purpose: 'COURSE' } as BankView
let last: Draft

// Dựng đúng biểu mẫu của kiểu và ghi lại bản nháp để so với đặc tả backend (question-types-d154.md).
function Harness({ type, layout = null }: { type: QuestionType; layout?: Obj | null }) {
  const [draft, setDraft] = useState(() => emptyDraft(type))
  last = draft
  const { Editor } = REGISTRY[type]
  return <Editor draft={draft} patch={(p) => setDraft((d) => ({ ...d, ...p }))} errors={{}} elements={[]} layout={layout} />
}
const body = () => toRequest(last, bank)
const setup = (type: QuestionType, layout?: Obj | null) => render(<Harness type={type} layout={layout} />)

const FORM = { kind: 'FORM', rows: [{ label: 'Name', slot: 'q1' }, { label: 'Phone', value: '0123' }, { label: 'City', slot: 'q2' }] }
const OPTIONS = { kind: 'OPTIONS', options: [{ id: 'i', text: 'Heading one' }, { id: 'ii', text: 'Heading two' }], reuse: false }

describe('LETTER_ORDER', () => {
  test('nhập từ sinh khối chữ khớp answerKey; Xáo và đổi thứ tự giữ nguyên tập chữ', async () => {
    setup('LETTER_ORDER')
    await userEvent.type(screen.getByLabelText('Từ đúng'), 'cat')
    const sorted = () => (last.payload.letters as { ch: string }[]).map((l) => l.ch).sort().join('')
    expect(body().answerKey).toEqual({ word: 'cat' })
    expect((body().payload as Obj).letters).toHaveLength(3)
    expect(sorted()).toBe('act')
    expect(new Set((last.payload.letters as { id: string }[]).map((l) => l.id)).size).toBe(3)
    await userEvent.click(screen.getByRole('button', { name: 'Xáo' }))
    expect(sorted()).toBe('act')
    const first = last.payload.letters[0].ch
    await userEvent.click(screen.getByRole('button', { name: 'Đưa khối 1 sang phải' }))
    expect(last.payload.letters[1].ch).toBe(first)
    expect(sorted()).toBe('act')
  })
})

describe('dạng gõ chữ', () => {
  test('DICTATION: accepted và maxWords, không có slot', async () => {
    setup('DICTATION')
    await userEvent.type(screen.getByLabelText('Đáp án chấp nhận'), 'He goes to school.{enter}He walks to school.')
    await userEvent.type(screen.getByLabelText('Số từ tối đa (không bắt buộc)'), '6')
    expect(body()).toMatchObject({ type: 'DICTATION', payload: { maxWords: 6 }, answerKey: { accepted: ['He goes to school.', 'He walks to school.'] } })
  })

  test('LISTEN_FORM: slot chọn từ danh sách chỗ trống của phiếu', async () => {
    setup('LISTEN_FORM', FORM)
    const slot = screen.getByLabelText('Chỗ trống của câu')
    expect(Array.from((slot as HTMLSelectElement).options).map((o) => o.value)).toEqual(['', 'q1', 'q2'])
    await userEvent.selectOptions(slot, 'q2')
    await userEvent.type(screen.getByLabelText('Đáp án chấp nhận'), 'London')
    expect(body()).toMatchObject({ payload: { slot: 'q2' }, answerKey: { accepted: ['London'] } })
  })

  test('IELTS_SENTENCE: before/after tùy chọn, bỏ khóa khi để trống', async () => {
    setup('IELTS_SENTENCE')
    await userEvent.type(screen.getByLabelText('Phần trước chỗ trống (không bắt buộc)'), 'The museum opens at')
    await userEvent.type(screen.getByLabelText('Phần sau chỗ trống (không bắt buộc)'), 'a')
    await userEvent.clear(screen.getByLabelText('Phần sau chỗ trống (không bắt buộc)'))
    await userEvent.type(screen.getByLabelText('Đáp án chấp nhận'), 'nine')
    expect(body().payload).toEqual({ before: 'The museum opens at' })
    expect(body().answerKey).toEqual({ accepted: ['nine'] })
  })

  test('IELTS_DIAGRAM: slot từ các điểm của sơ đồ và description', async () => {
    setup('IELTS_DIAGRAM', { kind: 'DIAGRAM', assetId: 1, points: [{ slot: 'd1', x: 1, y: 2 }] })
    await userEvent.selectOptions(screen.getByLabelText('Chỗ trống của câu'), 'd1')
    await userEvent.type(screen.getByLabelText('Mô tả vị trí nhãn'), 'top left valve')
    await userEvent.type(screen.getByLabelText('Đáp án chấp nhận'), 'valve')
    expect(body().payload).toEqual({ slot: 'd1', description: 'top left valve' })
  })

  test('IELTS_TABLE đọc chỗ trống {slot} trong các ô; IELTS_SHORT chỉ cần question', async () => {
    const { unmount } = render(<Harness type="IELTS_TABLE" layout={{ kind: 'TABLE', header: ['a', 'b'], rows: [['x {t1}', 'y'], ['{t2}', '']] }} />)
    expect(screen.getByRole('option', { name: 't2' })).toBeInTheDocument()
    unmount()
    setup('IELTS_SHORT')
    await userEvent.type(screen.getByLabelText('Câu hỏi'), 'Who built it?')
    await userEvent.type(screen.getByLabelText('Đáp án chấp nhận'), 'Romans')
    await userEvent.type(screen.getByLabelText('Số từ tối đa (không bắt buộc)'), '2')
    expect(body()).toMatchObject({ payload: { question: 'Who built it?', maxWords: 2 }, answerKey: { accepted: ['Romans'] } })
  })
})

describe('dạng chọn một giá trị', () => {
  test('IELTS_TFNG mặc định TRUE, đổi sang NOT_GIVEN', async () => {
    setup('IELTS_TFNG')
    await userEvent.type(screen.getByLabelText('Phát biểu'), 'The tower is old.')
    expect(body()).toMatchObject({ payload: { statement: 'The tower is old.' }, answerKey: { value: 'TRUE' } })
    await userEvent.click(screen.getByLabelText('NOT_GIVEN'))
    expect(body().answerKey).toEqual({ value: 'NOT_GIVEN' })
  })

  test('IELTS_YNNG dùng YES/NO/NOT_GIVEN', async () => {
    setup('IELTS_YNNG')
    await userEvent.click(screen.getByLabelText('NO'))
    expect(body().answerKey).toEqual({ value: 'NO' })
  })

  test('IELTS_MATCH_HEADINGS: paragraph và option là id trong layout', async () => {
    setup('IELTS_MATCH_HEADINGS', OPTIONS)
    await userEvent.type(screen.getByLabelText('Đoạn (ví dụ Paragraph B)'), 'Paragraph B')
    await userEvent.selectOptions(screen.getByLabelText('Đáp án đúng'), 'ii')
    expect(body()).toMatchObject({ payload: { paragraph: 'Paragraph B' }, answerKey: { option: 'ii' } })
  })

  test('IELTS_SUMMARY_BANK: slot của đoạn văn và word từ hộp từ', async () => {
    setup('IELTS_SUMMARY_BANK', { kind: 'TEXT_BANK', html: '<p>The {s1} was {s2}.</p>', words: [{ id: 'w1', text: 'river' }, { id: 'w2', text: 'wide' }], reuse: false })
    await userEvent.selectOptions(screen.getByLabelText('Chỗ trống của câu'), 's2')
    await userEvent.selectOptions(screen.getByLabelText('Đáp án đúng'), 'w2')
    expect(body()).toMatchObject({ payload: { slot: 's2' }, answerKey: { word: 'w2' } })
  })

  test('IELTS_MAP: label và option là nhãn điểm trên bản đồ', async () => {
    setup('IELTS_MAP', { kind: 'MAP', assetId: 1, options: [{ id: 'A', x: 1, y: 1 }, { id: 'B', x: 2, y: 2 }] })
    await userEvent.type(screen.getByLabelText('Tên địa điểm (ví dụ Café)'), 'Café')
    await userEvent.selectOptions(screen.getByLabelText('Đáp án đúng'), 'B')
    expect(body()).toMatchObject({ payload: { label: 'Café' }, answerKey: { option: 'B' } })
  })
})
