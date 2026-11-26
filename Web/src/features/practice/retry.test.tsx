import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'
import type { Bank, CheckState, Progress } from './types'

// D-150 (FR-EX-04): thử lại kèm gợi ý, Cam mở khi kiểm tra sai.
const bank = (mode: 'PRACTICE' | 'EXAM' = 'PRACTICE', hints = true): Bank => ({
  id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode, timeLimitSec: null,
  questions: [{
    id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Chọn', payload: {},
    choices: [{ id: 5, label: 'A', content: 'So' }, { id: 6, label: 'B', content: 'But' }],
    hints: hints ? [{ id: 1, content: 'Nghĩ về quan hệ nguyên nhân', detail: null }] : [],
  }],
})
const retry = (checks: CheckState[] = []): Progress => ({ attemptId: 9, paused: false, retry: { maxRetries: 1, checks } })

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

  test('sai còn lượt: Cam tự mở với gợi ý, sửa rồi kiểm tra lại thì đúng và khóa câu', async () => {
    const calls = wire(retry(), { questionId: 1, correct: false, checksLeft: 1 })
    renderAt('/bai-tap/7')
    const check = await screen.findByRole('button', { name: 'Kiểm tra' })
    expect(check).toBeDisabled() // chưa chọn đáp án
    await userEvent.click(screen.getByRole('radio', { name: /But/ }))
    await userEvent.click(check)
    expect(calls).toEqual([{ answer: { choiceId: 6 } }])
    const cam = await screen.findByRole('region', { name: 'Gợi ý của Cam' })
    expect(cam).toHaveTextContent('Chưa đúng, thử lại nhé')
    expect(cam).toHaveTextContent('Nghĩ về quan hệ nguyên nhân')
    expect(screen.getByRole('radio', { name: /So/ })).toBeEnabled() // còn lượt: sửa được
  })

  test('hết lượt thì khóa câu và chỉ về màn kết quả', async () => {
    wire(retry(), { questionId: 1, correct: false, checksLeft: 0 })
    renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('radio', { name: /But/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra' }))
    expect(await screen.findByText(/Xem đáp án ở màn kết quả/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Kiểm tra' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Gợi ý của Cam' })).not.toBeInTheDocument() // không tự mở
    expect(screen.getByRole('radio', { name: /So/ })).toBeDisabled()
  })

  test('câu không có gợi ý soạn sẵn: Cam vẫn mở và nói chưa đúng', async () => {
    wire(retry(), { questionId: 1, correct: false, checksLeft: 1 }, bank('PRACTICE', false))
    renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('radio', { name: /But/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra' }))
    expect(await screen.findByRole('region', { name: 'Gợi ý của Cam' })).toHaveTextContent('Chưa đúng, thử lại nhé')
  })

  test('mở lại lượt: câu đã đúng hoặc hết lượt khôi phục trạng thái khóa', async () => {
    wire(retry([{ questionId: 1, correct: true, checksLeft: 1 }]), { questionId: 1, correct: true, checksLeft: 1 })
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
    wire({ attemptId: 9, paused: false }, { questionId: 1, correct: true, checksLeft: 1 }, bank('EXAM'))
    renderAt('/bai-tap/7')
    await screen.findByRole('radio', { name: /So/ })
    expect(screen.queryByRole('button', { name: 'Kiểm tra' })).not.toBeInTheDocument()
  })
})
