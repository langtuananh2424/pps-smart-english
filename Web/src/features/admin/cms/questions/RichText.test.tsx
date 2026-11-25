import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RichText } from './RichText'

// jsdom không có API bố cục mà ProseMirror cần khi cuộn tới con trỏ.
const rect = { x: 0, y: 0, width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0, toJSON: () => ({}) }
Range.prototype.getClientRects = () => [] as unknown as DOMRectList
Range.prototype.getBoundingClientRect = () => rect as DOMRect
document.elementFromPoint = () => null

describe('RichText (TipTap)', () => {
  test('hiện nội dung ban đầu và thanh công cụ; không có nút ảnh khi thiếu upload', async () => {
    render(<RichText label="Đề bài" value="<p>Xin chào</p>" onChange={() => {}} />)
    expect(await screen.findByRole('textbox', { name: 'Đề bài' })).toHaveTextContent('Xin chào')
    expect(screen.getByRole('button', { name: 'In đậm' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Chèn bảng' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Chèn ảnh' })).not.toBeInTheDocument()
  })

  test('chế độ compact bỏ bảng; có upload thì có nút ảnh', async () => {
    render(<RichText compact label="Lựa chọn" value="" onChange={() => {}} upload={async () => ({ id: 1, url: 'u', width: 1, height: 1 })} />)
    await screen.findByRole('textbox', { name: 'Lựa chọn' })
    expect(screen.queryByRole('button', { name: 'Chèn bảng' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Chèn ảnh' })).toBeInTheDocument()
  })

  test('gõ chữ trả HTML qua onChange', async () => {
    const onChange = vi.fn()
    render(<RichText label="Đề bài" value="" onChange={onChange} />)
    await userEvent.click(await screen.findByRole('textbox', { name: 'Đề bài' }))
    await userEvent.keyboard('abc')
    await waitFor(() => expect(onChange).toHaveBeenLastCalledWith('<p>abc</p>'))
  })
})
