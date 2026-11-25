import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { setAccessToken } from '../../../../lib/api'
import { makeQueryClient } from '../../../../lib/queryClient'
import { apiError, json, mockApi } from '../../../../testUtils'
import { bankRoutes } from './routes'
import { uploadImage } from './upload'

// TipTap có test riêng; ở đây thay bằng ô chữ để kiểm biểu mẫu.
vi.mock('./RichText', () => ({
  RichText: ({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) => (
    <textarea aria-label={label} defaultValue={value} onChange={(e) => onChange(e.target.value)} />
  ),
}))

const C = '/api/admin/cms'
const bank = { id: 5, topicId: 2, lessonId: null, title: 'Bộ A', isTrial: false, status: 'DRAFT', questionCount: 2, timeLimitSec: null, mode: 'PRACTICE', purpose: 'COURSE', playbackMode: 'FREE', playLimit: null }
const choice = (id: number, label: string, ok: boolean) => ({ id, label, content: `nd ${label}`, contentHtml: null, isCorrect: ok, explanation: null })
const q = (id: number, sortOrder: number, extra: object = {}) => ({
  id, bankId: 5, type: 'CONNECTIVE_MCQ', prompt: `Đề ${id}`, payload: null, answerKey: null, choices: [choice(1, 'A', true), choice(2, 'B', false)],
  explanation: null, sortOrder, grammarElementCodes: [], answerVersion: 1, regradeRequired: false, skill: null, instruction: null, media: [], groupId: null,
  promptHtml: null, transcriptExcerpt: null, ...extra,
})
const detail = (over: object = {}, questions = [q(11, 0, { regradeRequired: true }), q(12, 1)]) => ({ bank: { ...bank, ...over }, questions, groups: [] })

function renderAt(path: string) {
  const router = createMemoryRouter(bankRoutes.map((r) => ({ ...r, path: `/${r.path}` })), { initialEntries: [path] })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

// Tester D-145: ô Ngưỡng đạt khi sửa bộ đã có ngưỡng tùy chỉnh.
describe('ô passPct', () => {
  const open = async (bodies: unknown[]) => {
    mockApi({
      [`${C}/banks/5`]: (body) => {
        if (!body) return json(200, detail({ passPct: 55 }))
        bodies.push(body)
        return json(200, { ...bank, passPct: 55 })
      },
      [`${C}/grammar-elements`]: () => json(200, []),
    })
    renderAt('/ngan-hang/5')
    await userEvent.click(await screen.findByRole('button', { name: 'Thuộc tính bộ' }))
    return screen.getByLabelText('Ngưỡng đạt (%)')
  }

  test('số thập phân 70.5 bị chặn phía trước, không gửi passPct lẻ (máy chủ sẽ âm thầm cắt thành 70)', async () => {
    const bodies: unknown[] = []
    const input = await open(bodies)
    await userEvent.clear(input)
    await userEvent.type(input, '70.5')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu thuộc tính' }))
    await new Promise((r) => setTimeout(r, 50))
    expect(bodies.filter((b) => (b as { passPct?: number }).passPct === 70.5)).toEqual([])
  })

  test('xóa trống ô ngưỡng (gợi ý "Để trống thì dùng 70") phải đưa bộ về 70, không âm thầm giữ 55', async () => {
    const bodies: unknown[] = []
    const input = await open(bodies)
    await userEvent.clear(input)
    await userEvent.click(screen.getByRole('button', { name: 'Lưu thuộc tính' }))
    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({ passPct: 70 })
  })
})
