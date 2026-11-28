import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { asg, board, detail, tree } from './fixtures'

const err = (status: number, code: string, message: string, details?: unknown) => json(status, { error: { code, message, details } })
const base = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/classes/3': () => json(200, detail),
  '/api/classes/3/assignable': () => json(200, tree),
  '/api/classes/3/progress': () => json(200, board),
  '/api/classes/3/assignments': () => json(200, [asg()]),
  '/api/classes/3/assignments/5/progress': () => json(200, []),
}

beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

async function throughSteps(user: ReturnType<typeof userEvent.setup>, bank = 'Bộ Unit 1') {
  await user.click(await screen.findByRole('radio', { name: new RegExp(bank) }))
  await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))
  fireEvent.change(await screen.findByLabelText('Hạn nộp (ngày)'), { target: { value: '2099-01-05' } })
  fireEvent.change(screen.getByLabelText('Hạn nộp (giờ)'), { target: { value: '18:30' } })
}

describe('giao bài 3 bước (FR-ASG-01)', () => {
  test('thành công: POST đúng body rồi sang chi tiết bài vừa giao', async () => {
    let body: any
    mockApi({ ...base, '/api/classes/3/assignments': (b, i) => (i?.method === 'POST' ? ((body = b), json(201, asg())) : json(200, [asg()])) })
    const user = userEvent.setup()
    renderAt('/giang-day/lop/3/giao-bai')
    await throughSteps(user)
    await user.click(screen.getByRole('radio', { name: 'Thi' }))
    await user.click(screen.getByRole('button', { name: 'Xem lại' }))
    expect(await screen.findByText('Chỉ bài được giao sẽ mở cho người nhận.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Giao bài' }))
    expect(await screen.findByRole('heading', { name: 'Bộ Unit 1' })).toBeInTheDocument()
    expect(body).toMatchObject({ bankId: 41, studentIds: [], mode: 'EXAM' })
    expect(body.dueAt).toBe(new Date('2099-01-05T18:30').toISOString())
  })

  test('câu Viết: không có chế độ, gửi questionId; tìm theo tên lọc danh sách', async () => {
    let body: any
    mockApi({ ...base, '/api/classes/3/assignments': (b, i) => (i?.method === 'POST' ? ((body = b), json(201, asg())) : json(200, [asg()])) })
    const user = userEvent.setup()
    renderAt('/giang-day/lop/3/giao-bai')
    await user.type(await screen.findByLabelText('Tên bài'), 'thư')
    expect(screen.queryByRole('radio', { name: /Bộ Unit 1/ })).not.toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: /Viết thư/ }))
    await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    expect(screen.queryByRole('radio', { name: 'Thi' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: 'Chọn học viên' }))
    expect(screen.queryByLabelText('Bình đã rời')).not.toBeInTheDocument() // chỉ học viên ACTIVE
    await user.click(screen.getByLabelText('Nga'))
    fireEvent.change(screen.getByLabelText('Hạn nộp (ngày)'), { target: { value: '2099-01-05' } })
    await user.click(screen.getByRole('button', { name: 'Xem lại' }))
    await user.click(screen.getByRole('button', { name: 'Giao bài' }))
    await waitFor(() => expect(body).toMatchObject({ questionId: 77, studentIds: [22] }))
    expect(body.mode).toBeUndefined()
  })

  test('409 assignment_duplicate: màn Chưa thể giao bài, Chỉnh sửa về bước chọn bài', async () => {
    mockApi({ ...base, '/api/classes/3/assignments': (_b, i) => (i?.method === 'POST' ? err(409, 'assignment_duplicate', 'Bài này đã được giao cho lớp') : json(200, [])) })
    const user = userEvent.setup()
    renderAt('/giang-day/lop/3/giao-bai')
    await throughSteps(user)
    await user.click(screen.getByRole('button', { name: 'Xem lại' }))
    await user.click(screen.getByRole('button', { name: 'Giao bài' }))
    expect(await screen.findByRole('heading', { name: 'Chưa thể giao bài' })).toBeInTheDocument()
    expect(screen.getByText('Bài này đã được giao cho lớp')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Chỉnh sửa' }))
    expect(await screen.findByText(/Bước 1\/3/)).toBeInTheDocument()
  })

  test('400 hạn nộp trong quá khứ: hiện lỗi, Chỉnh sửa về bước hạn nộp', async () => {
    mockApi({ ...base, '/api/classes/3/assignments': (_b, i) => (i?.method === 'POST' ? err(400, 'validation_error', 'Dữ liệu không hợp lệ', { dueAt: 'Hạn nộp phải ở tương lai' }) : json(200, [])) })
    const user = userEvent.setup()
    renderAt('/giang-day/lop/3/giao-bai')
    await throughSteps(user)
    await user.click(screen.getByRole('button', { name: 'Xem lại' }))
    await user.click(screen.getByRole('button', { name: 'Giao bài' }))
    expect(await screen.findByText('Hạn nộp phải ở tương lai')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Chỉnh sửa' }))
    expect(await screen.findByLabelText('Hạn nộp (ngày)')).toBeInTheDocument()
  })
})

describe('mở sớm Unit (FR-CLS-06)', () => {
  const unlock = { id: 8, topicId: 2, userId: null, grantedBy: 7, grantedByName: 'Cô Hoa', grantedAt: '2026-11-20T03:00:00Z', revokedAt: null }

  test('hiện người mở, thu hồi có hộp xác nhận rồi DELETE', async () => {
    let deleted = false
    mockApi({ ...base, '/api/classes/3/unlocks': () => json(200, deleted ? [] : [unlock]), '/api/classes/3/unlocks/8': (_b, i) => ((deleted = i?.method === 'DELETE'), json(200, { ...unlock, revokedAt: '2026-11-21T00:00:00Z' })) })
    const user = userEvent.setup()
    renderAt('/giang-day/lop/3/mo-unit')
    expect(await screen.findByText('Cô Hoa')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'Cả lớp' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Thu hồi' }))
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Thu hồi' }))
    await waitFor(() => expect(deleted).toBe(true))
    expect(await screen.findByText('Chưa mở sớm Unit nào.')).toBeInTheDocument()
  })

  test.each([
    ['not_sequential', 'Khóa này không bật học tuần tự'],
    ['unlock_duplicate', 'Unit này đã được mở'],
  ])('409 %s hiện thông báo tiếng Việt', async (code, msg) => {
    mockApi({ ...base, '/api/classes/3/unlocks': (_b, i) => (i?.method === 'POST' ? err(409, code, 'x') : json(200, [])) })
    const user = userEvent.setup()
    renderAt('/giang-day/lop/3/mo-unit')
    await user.selectOptions(await screen.findByLabelText('Unit'), 'Unit 2')
    await user.click(screen.getByRole('button', { name: 'Mở Unit' }))
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Mở Unit' }))
    expect(await screen.findByText(msg)).toBeInTheDocument()
  })
})
