import { screen, within } from '@testing-library/react'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { asg, detail, row } from './fixtures'

// Tester D-174: màn chấm hiện bài làm học viên cho nhiều dạng câu, chỉ đọc; 404 không chặn form chấm; không chữ "AI".
const URL = '/giang-day/lop/3/bai-giao/5/bai-nop/91'
const REVIEW = '/api/classes/3/assignments/5/attempts/91/result'
const choices = [{ id: 5, label: 'A', content: 'One' }, { id: 6, label: 'B', content: 'Two' }, { id: 7, label: 'C', content: 'Three' }]
const questions = [
  { id: 1, type: 'CONNECTIVE_MATCH', prompt: 'Ghép vế câu', payload: { left: [{ id: 'l1', text: 'It rained' }], right: [{ id: 'r1', text: 'so we stayed' }, { id: 'r2', text: 'because sunny' }] } },
  { id: 2, type: 'CHOICE_MULTI', prompt: 'Chọn nhiều đáp án', payload: {}, choices },
  { id: 3, type: 'TRUE_FALSE', prompt: 'Đúng hay sai', payload: { options: ['TRUE', 'FALSE'] } },
  { id: 4, type: 'WORD_BANK', prompt: 'Điền từ trong hộp', payload: { text: '{1}', blanks: ['1'], words: ['because', 'so'] } },
  { id: 5, type: 'MATCH_OPTIONS', prompt: 'Nối đoạn với ý', payload: { items: [{ id: 'i1', text: 'Para 1' }], options: [{ id: 'o1', text: 'More jobs' }, { id: 'o2', text: 'Less rain' }] } },
  { id: 6, type: 'GRAMMAR_BUILD', prompt: 'Sắp xếp khối thành câu', payload: { blocks: [{ id: 'b1', text: 'She' }, { id: 'b2', text: 'runs' }, { id: 'b3', text: 'fast' }] } },
  { id: 7, type: 'LETTER_ORDER', prompt: 'Sắp xếp chữ cái', payload: { letters: [{ id: 'x', ch: 'c' }, { id: 'y', ch: 'a' }, { id: 'z', ch: 't' }] } },
  { id: 8, type: 'IELTS_SUMMARY_BANK', prompt: 'Chọn từ trong nhóm', payload: {}, groupId: 70 },
  { id: 9, type: 'CONNECTIVE_MCQ', prompt: 'Câu học viên bỏ trống', payload: {}, choices },
  { id: 10, type: 'WRITING', prompt: 'Viết đoạn văn', payload: {} },
]
const results = [
  { questionId: 1, isCorrect: false, correctAnswer: { pairs: { l1: 'r1' } } },
  { questionId: 2, isCorrect: false, correctAnswer: { choiceIds: [5, 6] } },
  { questionId: 3, isCorrect: false, correctAnswer: { answer: 'TRUE' } },
  { questionId: 4, isCorrect: true, correctAnswer: { blanks: { 1: 'because' } } },
  { questionId: 5, isCorrect: false, correctAnswer: { matches: { i1: 'o1' } } },
  { questionId: 6, isCorrect: false, correctAnswer: { order: ['b1', 'b2', 'b3'] } },
  { questionId: 7, isCorrect: true, correctAnswer: { word: 'cat' } },
  { questionId: 8, isCorrect: true, correctAnswer: { word: 'w1' } },
  { questionId: 9, isCorrect: false, correctAnswer: { choiceId: 5 } },
]
const review = {
  attempt: { id: 91, bankId: 41, submittedAt: '2026-11-21T03:00:00Z', score: 3, maxScore: 10, results },
  bank: {
    id: 41, title: 'Bộ Unit 1', lessonId: null, isTrial: false, mode: 'PRACTICE', timeLimitSec: null, questions,
    groups: [{ id: 70, title: 'Nhóm tóm tắt', layout: { kind: 'TEXT_BANK', html: '', words: [{ id: 'w1', text: 'sunny' }, { id: 'w2', text: 'rainy' }] } }],
  },
  answers: [
    { questionId: 1, answer: { pairs: { l1: 'r2' } }, isCorrect: false, credit: 0 },
    { questionId: 2, answer: { choiceIds: [5, 7] }, isCorrect: false, credit: 0.5 },
    { questionId: 3, answer: { value: 'FALSE' }, isCorrect: false, credit: 0 },
    { questionId: 4, answer: { blanks: { 1: 'because' } }, isCorrect: true, credit: 1 },
    { questionId: 5, answer: { matches: { i1: 'o2' } }, isCorrect: false, credit: 0 },
    { questionId: 6, answer: { order: ['b2', 'b1', 'b3'] }, isCorrect: false, credit: 0 },
    { questionId: 7, answer: { order: ['x', 'y', 'z'] }, isCorrect: true, credit: 1 },
    { questionId: 8, answer: { word: 'w1' }, isCorrect: true, credit: 1 },
    { questionId: 9, answer: null, isCorrect: false, credit: 0 },
  ],
}
const base = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/classes/3': () => json(200, detail),
  '/api/classes/3/assignments': () => json(200, [asg()]),
  '/api/classes/3/assignments/5/progress': () => json(200, [row()]),
  '/api/classes/3/assignments/5/attempts/91/grades': () => json(200, []),
  '/api/grammar-elements': () => json(200, []),
}

beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

const item = (prompt: string) => screen.getByText(prompt).closest('li') as HTMLElement
const line = (li: HTMLElement, label: string) => within(li).getByText(label + ':').nextElementSibling?.textContent

describe('tester D-174: bài làm nhiều dạng câu', () => {
  test('mỗi dạng hiện đúng bài làm của học viên và đáp án đúng', async () => {
    mockApi({ ...base, [REVIEW]: () => json(200, review) })
    renderAt(URL)
    await screen.findByText('Ghép vế câu')
    expect(line(item('Ghép vế câu'), 'Bài làm')).toBe('It rained → because sunny')
    expect(line(item('Ghép vế câu'), 'Đáp án đúng')).toBe('It rained → so we stayed')
    expect(line(item('Chọn nhiều đáp án'), 'Bài làm')).toBe('A. One; C. Three')
    expect(line(item('Chọn nhiều đáp án'), 'Đáp án đúng')).toBe('A. One; B. Two')
    expect(within(item('Chọn nhiều đáp án')).getByText('0.5 điểm')).toBeInTheDocument()
    expect(line(item('Đúng hay sai'), 'Bài làm')).toBe('False')
    expect(line(item('Đúng hay sai'), 'Đáp án đúng')).toBe('True')
    expect(line(item('Điền từ trong hộp'), 'Bài làm')).toBe('1: because')
    expect(line(item('Nối đoạn với ý'), 'Bài làm')).toBe('Para 1 → B. Less rain')
    expect(line(item('Nối đoạn với ý'), 'Đáp án đúng')).toBe('Para 1 → A. More jobs')
    const build = item('Sắp xếp khối thành câu')
    expect(within(build).getByText('1. runs')).toBeInTheDocument()
    expect(within(build).getByText('She runs fast')).toBeInTheDocument()
    expect(line(item('Sắp xếp chữ cái'), 'Bài làm')).toBe('cat')
    expect(line(item('Chọn từ trong nhóm'), 'Bài làm')).toBe('sunny')
    expect(line(item('Câu học viên bỏ trống'), 'Bài làm')).toBe('Chưa làm')
    expect(within(item('Viết đoạn văn')).getByText(/làm ở màn Viết\/Nói/)).toBeInTheDocument()
    // chỉ đọc: không nút làm lại, không ô nhập đáp án
    expect(screen.queryByRole('button', { name: /làm lại|luyện thêm|kiểm tra|nộp bài/i })).not.toBeInTheDocument()
    expect(screen.queryAllByRole('radio')).toHaveLength(0)
    expect(screen.queryAllByRole('checkbox')).toHaveLength(0)
    expect(document.body.textContent).not.toMatch(/\bAI\b/)
  })

  test('404 chỉ báo ở khối bài làm, form chấm vẫn dùng được', async () => {
    mockApi({ ...base, [REVIEW]: () => json(404, { error: { code: 'attempt_not_found', message: 'Không tìm thấy lượt đã nộp của bài này' } }) })
    renderAt(URL)
    expect(await screen.findByText(/Không tìm thấy lượt đã nộp của bài này/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Lưu nhận xét' })).toBeEnabled()
    expect(screen.getByLabelText('Điểm mới')).toBeEnabled()
    expect(document.body.textContent).not.toMatch(/\bAI\b/)
  })

  test('lượt Nghe ép (results null): báo không có kết quả từng câu, không dựng câu nào', async () => {
    mockApi({ ...base, [REVIEW]: () => json(200, { ...review, attempt: { ...review.attempt, score: null, results: null, forced: { attemptCount: 1 } } }) })
    renderAt(URL)
    expect(await screen.findByText(/Chưa có kết quả từng câu/)).toBeInTheDocument()
    expect(screen.queryByText('Ghép vế câu')).not.toBeInTheDocument()
  })
})
