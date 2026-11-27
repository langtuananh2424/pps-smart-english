import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'
import type { Bank, CheckState, Progress } from './types'

// D-150, D-163 (FR-EX-04): thử lại; Cam chỉ tự mở từ lần sai thứ 2, hết lượt thì hiện đáp án và giải thích.
const bank = (mode: 'PRACTICE' | 'EXAM' = 'PRACTICE', hints = true): Bank => ({
  id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode, timeLimitSec: null,
  questions: [{
    id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Chọn', payload: {},
    choices: [{ id: 5, label: 'A', content: 'So' }, { id: 6, label: 'B', content: 'But' }],
    hints: hints ? [{ id: 1, content: 'Nghĩ về quan hệ nguyên nhân', detail: null }] : [],
  }],
})
const retry = (checks: CheckState[] = []): Progress => ({ attemptId: 9, paused: false, retry: { maxRetries: 2, hintAfterWrong: 2, checks } })

describe('thử lại kèm gợi ý', () => {
  beforeEach(() => setAccessToken('t'))
  const wire = (progress: Progress, check: CheckState | Response, b = bank()) => {
    const calls: unknown[] = []
    mockApi({
      '/api/auth/refresh': () => json(200, auth),
      '/api/banks/7': () => json(200, b),
      '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [{ id: 9, bankId: 7, startedAt: 'x' }] }),
      '/api/attempts/9/progress': () => json(200, progress),
      '/api/attempts/9/questions/1/check': (body) => {
        calls.push(body)
        return check instanceof Response ? check : json(200, check)
      },
    })
    return calls
  }

  test('sai lần đầu: chỉ báo Cần sửa, thử lại nhé, Cam không tự mở, chưa lộ đáp án', async () => {
    const calls = wire(retry(), { questionId: 1, correct: false, checksLeft: 2, wrongCount: 1 })
    renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('radio', { name: /But/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra' }))
    expect(await screen.findByText('Cần sửa, thử lại nhé')).toBeInTheDocument()
    expect(calls).toEqual([{ answer: { choiceId: 6 } }])
    expect(screen.queryByRole('region', { name: 'Gợi ý của Cam' })).not.toBeInTheDocument()
    expect(screen.queryByText('Đáp án đúng:')).not.toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /So/ })).toBeEnabled()
  })

  test('sai lần thứ 2 còn lượt: Cam tự mở với gợi ý', async () => {
    const calls = wire(retry(), { questionId: 1, correct: false, checksLeft: 1, wrongCount: 2 })
    renderAt('/bai-tap/7')
    const check = await screen.findByRole('button', { name: 'Kiểm tra' })
    expect(check).toBeDisabled() // chưa chọn đáp án
    await userEvent.click(screen.getByRole('radio', { name: /But/ }))
    await userEvent.click(check)
    expect(calls).toEqual([{ answer: { choiceId: 6 } }])
    const cam = await screen.findByRole('region', { name: 'Gợi ý của Cam' })
    expect(cam).toHaveTextContent('Cần sửa, thử lại nhé')
    expect(cam).toHaveTextContent('Nghĩ về quan hệ nguyên nhân')
    expect(screen.getByRole('radio', { name: /So/ })).toBeEnabled() // còn lượt: sửa được
  })

  test('hết lượt mà vẫn sai: khóa câu, hiện đáp án đúng và giải thích, Cam không tự mở', async () => {
    wire(retry(), { questionId: 1, correct: false, checksLeft: 0, wrongCount: 3, correctAnswer: { choiceId: 5 }, explanation: 'Vì quan hệ nhân quả' })
    renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('radio', { name: /But/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra' }))
    expect(await screen.findByText('Đáp án đúng:')).toBeInTheDocument()
    expect(screen.getByText('A. So')).toBeInTheDocument()
    expect(screen.getByText('Vì quan hệ nhân quả')).toBeInTheDocument()
    expect(screen.queryByText(/màn kết quả/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Kiểm tra' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Gợi ý của Cam' })).not.toBeInTheDocument() // không tự mở
    expect(screen.getByRole('radio', { name: /So/ })).toBeDisabled()
  })

  test('câu không có gợi ý soạn sẵn: Cam vẫn mở và nói chưa đúng', async () => {
    wire(retry(), { questionId: 1, correct: false, checksLeft: 1, wrongCount: 2 }, bank('PRACTICE', false))
    renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('radio', { name: /But/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra' }))
    expect(await screen.findByRole('region', { name: 'Gợi ý của Cam' })).toHaveTextContent('Cần sửa, thử lại nhé')
  })

  test('tải lại trang: câu đã khóa sai vẫn thấy đáp án và giải thích từ progress', async () => {
    wire(retry([{ questionId: 1, correct: false, checksLeft: 0, wrongCount: 3, correctAnswer: { choiceId: 6 }, explanation: 'Vì nhưng' }]), { questionId: 1, correct: false, checksLeft: 0, wrongCount: 3 })
    renderAt('/bai-tap/7')
    expect(await screen.findByText('B. But')).toBeInTheDocument()
    expect(screen.getByText('Vì nhưng')).toBeInTheDocument()
  })

  test('mở lại lượt: câu đã đúng hoặc hết lượt khôi phục trạng thái khóa', async () => {
    wire(retry([{ questionId: 1, correct: true, checksLeft: 1, wrongCount: 1 }]), { questionId: 1, correct: true, checksLeft: 1, wrongCount: 1 })
    renderAt('/bai-tap/7')
    expect(await screen.findByText('Đúng rồi!')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Kiểm tra' })).not.toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /So/ })).toBeDisabled()
  })

  test('máy chủ báo hết lượt thì khóa câu và hiện lỗi', async () => {
    wire(retry(), apiError(409, 'Câu này đã hết lượt thử'))
    renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('radio', { name: /But/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Câu này đã hết lượt thử')
  })

  test('lượt không áp dụng (không có retry, như bộ EXAM): không có nút Kiểm tra', async () => {
    wire({ attemptId: 9, paused: false }, { questionId: 1, correct: true, checksLeft: 2, wrongCount: 0 }, bank('EXAM'))
    renderAt('/bai-tap/7')
    await screen.findByRole('radio', { name: /So/ })
    expect(screen.queryByRole('button', { name: 'Kiểm tra' })).not.toBeInTheDocument()
  })
})
