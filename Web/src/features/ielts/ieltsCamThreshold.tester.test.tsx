import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import type { Bank, CheckState } from '../practice/types'

// D-163 (tester): Cam trong khung IELTS theo ngưỡng sai; hết lượt thì không còn mời "thử lại".
const mcq = (id: number) => ({
  id, type: 'CONNECTIVE_MCQ', prompt: `Câu ${id}?`, payload: {}, groupId: 1,
  choices: [{ id: id * 10 + 1, label: 'A', content: `Đúng ${id}` }, { id: id * 10 + 2, label: 'B', content: `Sai ${id}` }],
})
const reading = (): Bank => ({
  id: 7, title: 'Reading 1', lessonId: null, isTrial: false, mode: 'PRACTICE', timeLimitSec: 600, skill: 'READING', ielts: true,
  questions: [mcq(1), mcq(2)],
  groups: [{ id: 1, title: 'Passage 1', instruction: null, passageHtml: '<p>Bài đọc</p>', sortOrder: 0, hasAudio: false, audioDurationMs: null }],
})

// afterExhausted: check state máy chủ trả ở progress sau khi nhận 409 retry_exhausted.
function wire(replies: (CheckState | Response)[], hintAfterWrong = 2, afterExhausted: CheckState[] = []) {
  let i = 0
  let exhausted = false
  mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/banks/7': () => json(200, reading()),
    '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [{ id: 9, bankId: 7, startedAt: 'x' }] }),
    '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false, remainingSec: 600, mode: 'PRACTICE', retry: { maxRetries: 2, hintAfterWrong, checks: exhausted ? afterExhausted : [] } }),
    '/api/attempts/9/hints': () => json(200, []),
    '/api/attempts/9/draft': () => json(200, { attemptId: 9, paused: false, mode: 'PRACTICE' }),
    '/api/attempts/9/questions/1/check': () => { const r = replies[Math.min(i++, replies.length - 1)]; if (r instanceof Response) exhausted = true; return r instanceof Response ? r : json(200, r) },
  })
}
const wrong = (n: number, extra: Partial<CheckState> = {}): CheckState => ({ questionId: 1, correct: false, checksLeft: 3 - n, wrongCount: n, ...extra })

async function checkTimes(n: number) {
  const q1 = await screen.findByRole('article', { name: 'Câu 1' })
  await userEvent.click(within(q1).getByRole('radio', { name: /Sai 1/ }))
  for (let k = 0; k < n; k++) await userEvent.click(within(q1).getByRole('button', { name: 'Kiểm tra' }))
  return q1
}

beforeEach(() => setAccessToken('t'))

describe('IELTS Luyện tập: ngưỡng Cam và hết lượt (D-163)', () => {
  test('sai lần 1 không mở Cam, lần 2 mở', async () => {
    wire([wrong(1), wrong(2)])
    renderAt('/ielts/7')
    await checkTimes(1)
    expect(await screen.findByText('Cần sửa, thử lại nhé')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Gợi ý của Cam' })).not.toBeInTheDocument()
    await userEvent.click(within(screen.getByRole('article', { name: 'Câu 1' })).getByRole('button', { name: 'Kiểm tra' }))
    expect(await screen.findByRole('region', { name: 'Gợi ý của Cam' })).toHaveTextContent('Cần sửa, thử lại nhé')
  })

  test('sai lần 3 (hết lượt) sau khi Cam đã mở: hiện đáp án, Cam không còn mời thử lại', async () => {
    wire([wrong(1), wrong(2), wrong(3, { correctAnswer: { choiceId: 11 }, explanation: 'Vì đoạn A' })])
    renderAt('/ielts/7')
    const q1 = await checkTimes(3)
    expect(await within(q1).findByText('Vì đoạn A')).toBeInTheDocument()
    expect(within(q1).getByText('A. Đúng 1')).toBeInTheDocument()
    const cam = screen.queryByRole('region', { name: 'Gợi ý của Cam' })
    expect(cam?.textContent ?? '').not.toContain('thử lại nhé')
  })

  test('hintAfterWrong lớn hơn số lần sai còn lượt: Cam không bao giờ tự mở (cấu hình 3, maxRetries 2)', async () => {
    wire([wrong(1), wrong(2), wrong(3, { correctAnswer: { choiceId: 11 } })], 3)
    renderAt('/ielts/7')
    await checkTimes(3)
    expect(await screen.findByText('Đáp án đúng:')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Gợi ý của Cam' })).not.toBeInTheDocument()
  })

  // Hai tab: tab kia đã dùng lượt cuối, máy chủ trả 409 retry_exhausted; câu khóa sai thì vẫn phải thấy đáp án (D-163 mục 4).
  test('máy chủ báo retry_exhausted: câu khóa sai vẫn hiện đáp án đúng', async () => {
    wire([wrong(1), json(409, { error: { code: 'retry_exhausted', message: 'Câu này đã hết lượt thử' } })], 2, [wrong(3, { correctAnswer: { choiceId: 11 } })])
    renderAt('/ielts/7')
    const q1 = await checkTimes(2)
    expect(await screen.findByRole('alert')).toHaveTextContent('Câu này đã hết lượt thử')
    expect(within(q1).queryByRole('button', { name: 'Kiểm tra' })).not.toBeInTheDocument()
    expect(within(q1).getByText('Đáp án đúng:')).toBeInTheDocument()
  })
})
