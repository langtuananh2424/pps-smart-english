import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../../../lib/api'
import { makeQueryClient } from '../../../../lib/queryClient'
import { json, mockApi } from '../../../../testUtils'
import { GroupForm } from './GroupForm'
import type { GroupView } from './types'
import { uploadVideo } from './upload'

vi.mock('./RichText', () => ({
  RichText: ({ label }: { label: string }) => <textarea aria-label={label} />,
}))
vi.mock('./upload', () => ({ uploadAudio: vi.fn(), uploadVideo: vi.fn() }))

const saved = (over: Partial<GroupView> = {}): GroupView => ({
  id: 3, bankId: 5, sortOrder: 0, title: null, instruction: null, passageHtml: null, audioAssetId: null, audioDurationMs: null,
  transcriptHtml: null, videoAssetId: null, videoYoutubeId: null, ...over,
})

function setup(group: GroupView | null, body: { current?: unknown }) {
  const calls = mockApi({
    '/api/admin/cms/banks/5/groups': (_u, init) => { body.current = JSON.parse(String(init?.body)); return json(201, saved()) },
    '/api/admin/cms/groups/3': (_u, init) => { body.current = JSON.parse(String(init?.body)); return json(200, saved()) },
  })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <GroupForm bankId={5} group={group} nextSortOrder={0} onSaved={() => {}} onDeleted={() => {}} />
    </QueryClientProvider>,
  )
  return calls
}

beforeEach(() => setAccessToken(null))

describe('GroupForm video', () => {
  test('tải mp4 có tiến trình rồi gửi videoAssetId, link YouTube rỗng', async () => {
    vi.mocked(uploadVideo).mockImplementation(async (_f, onProgress) => { onProgress(40); return { id: 77 } })
    const body: { current?: unknown } = {}
    setup(null, body)
    await userEvent.upload(screen.getByLabelText('Tệp video'), new File(['x'], 'v.mp4', { type: 'video/mp4' }))
    expect(await screen.findByText('Đã gắn video.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Lưu nhóm' }))
    await waitFor(() => expect(body.current).toMatchObject({ videoAssetId: 77, videoYoutube: '' }))
  })

  test('dán link YouTube gỡ video file và gửi link', async () => {
    const body: { current?: unknown } = {}
    setup(saved({ videoAssetId: 9 }), body)
    expect(screen.getByText('Đã gắn video.')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Link YouTube'), 'https://youtu.be/dQw4w9WgXcQ')
    expect(screen.queryByText('Đã gắn video.')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Lưu nhóm' }))
    await waitFor(() => expect(body.current).toMatchObject({ videoAssetId: null, videoYoutube: 'https://youtu.be/dQw4w9WgXcQ' }))
  })

  test('tải video lỗi hiện thông báo và không gắn video', async () => {
    vi.mocked(uploadVideo).mockRejectedValue(new Error('Tệp vượt quá 100 MB.'))
    setup(null, {})
    await userEvent.upload(screen.getByLabelText('Tệp video'), new File(['x'], 'v.mp4', { type: 'video/mp4' }))
    expect(await screen.findByText('Tệp vượt quá 100 MB.')).toBeInTheDocument()
    expect(screen.queryByText('Đã gắn video.')).not.toBeInTheDocument()
  })
})
