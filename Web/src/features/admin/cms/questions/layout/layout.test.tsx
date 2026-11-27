import { QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../../../../lib/api'
import { makeQueryClient } from '../../../../../lib/queryClient'
import { apiError, json, mockApi } from '../../../../../testUtils'
import { GroupForm } from '../GroupForm'
import type { GroupView } from '../types'
import { uploadImage } from '../upload'
import { layoutSlots, toLayoutBody } from './layoutModel'

vi.mock('../RichText', () => ({
  RichText: ({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) => (
    <textarea aria-label={label} defaultValue={value} onChange={(e) => onChange(e.target.value)} />
  ),
}))
vi.mock('../upload', () => ({ uploadAudio: vi.fn(), uploadVideo: vi.fn(), uploadImage: vi.fn() }))
vi.spyOn(window, 'confirm').mockReturnValue(true)

const view = (over: Partial<GroupView> = {}): GroupView => ({
  id: 3, bankId: 5, sortOrder: 0, title: null, instruction: null, passageHtml: null, audioAssetId: null, audioDurationMs: null,
  transcriptHtml: null, videoAssetId: null, videoYoutubeId: null, ...over,
})

function setup(group: GroupView | null, fail?: Record<string, string>) {
  const sent: { body?: Record<string, unknown> } = {}
  mockApi({
    '/api/admin/cms/banks/5/groups': (_u, init) => {
      sent.body = JSON.parse(String(init?.body))
      return fail ? apiError(400, 'Dữ liệu không hợp lệ', fail) : json(201, view())
    },
    '/api/admin/cms/groups/3': (_u, init) => { sent.body = JSON.parse(String(init?.body)); return json(200, view()) },
  })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <GroupForm bankId={5} group={group} nextSortOrder={0} onSaved={() => {}} onDeleted={() => {}} />
    </QueryClientProvider>,
  )
  return sent
}
const save = () => userEvent.click(screen.getByRole('button', { name: 'Lưu nhóm' }))

beforeEach(() => setAccessToken(null))

describe('trình soạn layout của nhóm', () => {
  test('TABLE: thêm cột, thêm hàng, xóa hàng tạo đúng header/rows', async () => {
    const sent = setup(null)
    await userEvent.selectOptions(screen.getByLabelText('Kiểu bố cục'), 'TABLE')
    await userEvent.click(screen.getByRole('button', { name: 'Thêm cột' }))
    await userEvent.type(screen.getByLabelText('Tiêu đề cột 1'), 'Day')
    await userEvent.type(screen.getByLabelText('Tiêu đề cột 2'), 'Event')
    await userEvent.type(screen.getByLabelText('Tiêu đề cột 3'), 'Cost')
    await userEvent.type(screen.getByLabelText('Ô hàng 1 cột 2'), 'Tour')
    await userEvent.click(screen.getByRole('button', { name: 'Thêm hàng' }))
    await userEvent.click(screen.getByRole('button', { name: 'Thêm hàng' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xóa hàng 3' }))
    fireEvent.change(screen.getByLabelText('Ô hàng 2 cột 3'), { target: { value: '{c1}' } })
    await save()
    await waitFor(() => expect(sent.body?.layout).toEqual({ kind: 'TABLE', header: ['Day', 'Event', 'Cost'], rows: [['', 'Tour', ''], ['', '', '{c1}']] }))
  })

  test('DIAGRAM: tải ảnh, bấm lên ảnh đặt điểm theo %, bỏ image khi gửi', async () => {
    vi.mocked(uploadImage).mockResolvedValue({ id: 9, url: 'http://x/i.png', width: 400, height: 200 })
    const sent = setup(null)
    await userEvent.selectOptions(screen.getByLabelText('Kiểu bố cục'), 'DIAGRAM')
    await userEvent.upload(screen.getByLabelText('Tệp ảnh bố cục'), new File(['x'], 'i.png', { type: 'image/png' }))
    const canvas = await screen.findByTestId('points-canvas')
    canvas.getBoundingClientRect = () => ({ left: 100, top: 50, width: 200, height: 100, right: 300, bottom: 150, x: 100, y: 50, toJSON: () => ({}) })
    await userEvent.click(screen.getByRole('button', { name: 'Thêm điểm' }))
    fireEvent.click(canvas, { clientX: 150, clientY: 100 })
    await save()
    await waitFor(() => expect(sent.body?.layout).toEqual({ kind: 'DIAGRAM', assetId: 9, points: [{ slot: 'p1', x: 25, y: 50 }] }))
  })

  test('MAP mới có sẵn hai nhãn A, B; thêm điểm lấy nhãn C', async () => {
    const sent = setup(null)
    await userEvent.selectOptions(screen.getByLabelText('Kiểu bố cục'), 'MAP')
    await userEvent.click(screen.getByRole('button', { name: 'Thêm điểm' }))
    expect(screen.getByLabelText('Nhãn điểm 3')).toHaveValue('C')
    await save()
    await waitFor(() => expect((sent.body?.layout as { options: { id: string }[] }).options.map((o) => o.id)).toEqual(['A', 'B', 'C']))
  })

  test('FORM: dòng chỗ trống và dòng điền sẵn', async () => {
    const sent = setup(null)
    await userEvent.selectOptions(screen.getByLabelText('Kiểu bố cục'), 'FORM')
    await userEvent.type(screen.getByLabelText('Nhãn dòng 1'), 'Name')
    await userEvent.selectOptions(screen.getByLabelText('Kiểu dòng 1'), 'slot')
    await userEvent.type(screen.getByLabelText('Tên chỗ trống dòng 1'), 'q1')
    await userEvent.click(screen.getByRole('button', { name: 'Thêm dòng' }))
    await userEvent.type(screen.getByLabelText('Nhãn dòng 2'), 'Phone')
    await userEvent.selectOptions(screen.getByLabelText('Kiểu dòng 2'), 'value')
    await userEvent.type(screen.getByLabelText('Giá trị dòng 2'), '0123')
    await save()
    await waitFor(() => expect(sent.body?.layout).toEqual({ kind: 'FORM', rows: [{ label: 'Name', slot: 'q1' }, { label: 'Phone', value: '0123' }] }))
  })

  test('OPTIONS: sửa nhóm có layout giữ nguyên rồi gửi lại; gỡ bố cục gửi null', async () => {
    const layout = { kind: 'OPTIONS', title: 'Headings', options: [{ id: 'i', text: 'One' }, { id: 'ii', text: 'Two' }], reuse: true }
    const sent = setup(view({ layout }))
    await save()
    await waitFor(() => expect(sent.body?.layout).toEqual(layout))
    await userEvent.selectOptions(screen.getByLabelText('Kiểu bố cục'), '')
    await save()
    await waitFor(() => expect(sent.body?.layout).toBeNull())
  })

  test('hiện lỗi 400 đúng trường layout', async () => {
    setup(null, { 'layout.rows[0]': 'Dòng có chỗ trống thì không có value', 'layout.title': 'Tiêu đề quá dài' })
    await userEvent.selectOptions(screen.getByLabelText('Kiểu bố cục'), 'FORM')
    await save()
    expect(await screen.findByText('Dòng có chỗ trống thì không có value')).toBeInTheDocument()
    expect(screen.getByText('Tiêu đề quá dài')).toBeInTheDocument()
  })
})

describe('layoutModel', () => {
  test('layoutSlots đọc {slot} theo từng kiểu như backend', () => {
    expect(layoutSlots({ kind: 'NOTE', lines: [{ html: '<p>a {n1} b</p>' }, { html: '{n2} {n1}' }] })).toEqual(['n1', 'n2'])
    expect(layoutSlots({ kind: 'FORM', rows: [{ label: 'a', slot: 'f1' }, { label: 'b', value: 'v' }] })).toEqual(['f1'])
    expect(layoutSlots({ kind: 'FLOW', steps: ['x {s1}', 'y'] })).toEqual(['s1'])
    expect(layoutSlots({ kind: 'OPTIONS', options: [] })).toEqual([])
  })

  test('toLayoutBody bỏ image, title rỗng và src ảnh ký sẵn', () => {
    expect(toLayoutBody({ kind: 'TEXT', title: ' ', html: '<img src="http://s/x?sig=1" data-asset-id="4">' })).toEqual({ kind: 'TEXT', html: '<img data-asset-id="4">' })
    expect(toLayoutBody({ kind: 'MAP', assetId: 1, image: { url: 'u' }, options: [] })).toEqual({ kind: 'MAP', assetId: 1, options: [] })
  })
})
