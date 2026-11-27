import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { makeQueryClient } from '../../lib/queryClient'
import { apiError, json, mockApi } from '../../testUtils'
import { fetchQuestion } from '../speaking/api'
import { CamHint } from './CamHint'

const faqs = [{ id: 1, question: 'Khi nào dùng so?', answer: 'Chỉ kết quả.' }, { id: 2, question: 'Khác because?', answer: 'Chỉ nguyên nhân.' }]
const mount = (bankId?: number) =>
  render(<QueryClientProvider client={makeQueryClient()}><CamHint hints={[{ id: 9, content: 'Gợi ý 1' }]} faqBankId={bankId} /></QueryClientProvider>)

describe('Cam hỏi đáp (D-160)', () => {
  test('mở bong bóng: gợi ý và câu hỏi; chạm mở câu trả lời, mỗi lần một câu', async () => {
    mockApi({ '/api/banks/7/faqs': () => json(200, faqs) })
    mount(7)
    await screen.findByRole('button', { name: 'Hỏi Cam' })
    await userEvent.click(screen.getByRole('button', { name: 'Hỏi Cam' }))
    expect(screen.getByText('Gợi ý 1')).toBeInTheDocument()
    const q1 = await screen.findByRole('button', { name: /Khi nào dùng so/ })
    const q2 = screen.getByRole('button', { name: /Khác because/ })
    expect(q1).toHaveAttribute('aria-expanded', 'false')
    await userEvent.click(q1)
    expect(q1).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Chỉ kết quả.')).toBeInTheDocument()
    await userEvent.click(q2)
    expect(q1).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Chỉ kết quả.')).not.toBeInTheDocument()
    expect(screen.getByText('Chỉ nguyên nhân.')).toBeInTheDocument()
    await userEvent.click(q2)
    expect(screen.queryByText('Chỉ nguyên nhân.')).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  test('lượt Thi: không truyền bankId thì không gọi faqs', async () => {
    const calls = mockApi({})
    mount(undefined)
    await userEvent.click(screen.getByRole('button', { name: 'Hỏi Cam' }))
    expect(calls.some((u) => u.includes('/faqs'))).toBe(false)
    expect(screen.queryByText('Câu hỏi thường gặp')).not.toBeInTheDocument()
  })

  test('server 403 thì êm: chỉ còn gợi ý', async () => {
    mockApi({ '/api/banks/7/faqs': () => apiError(403, 'no') })
    mount(7)
    await userEvent.click(screen.getByRole('button', { name: 'Hỏi Cam' }))
    expect(screen.getByText('Gợi ý 1')).toBeInTheDocument()
    expect(screen.queryByText('Câu hỏi thường gặp')).not.toBeInTheDocument()
  })
})

test('fetchQuestion gửi attemptId khi có (D-148)', async () => {
  const calls = mockApi({ '/api/speaking/questions/8?attemptId=5': () => json(200, {}), '/api/speaking/questions/8?assignmentId=3&attemptId=5': () => json(200, {}) })
  await fetchQuestion(8, undefined, 5)
  await fetchQuestion(8, 3, 5)
  expect(calls).toEqual(['/api/speaking/questions/8?attemptId=5', '/api/speaking/questions/8?assignmentId=3&attemptId=5'])
})
