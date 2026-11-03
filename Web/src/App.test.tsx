import { render, screen } from '@testing-library/react'
import App from './App'

afterEach(() => vi.restoreAllMocks())

test('hiển thị danh sách khóa học từ API', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(
      JSON.stringify([{ id: 1, title: 'Giao tiếp phản xạ', description: 'Nói tự nhiên', levelLabel: 'Giao tiếp' }]),
    ),
  )
  render(<App />)
  expect(await screen.findByText('Giao tiếp phản xạ')).toBeInTheDocument()
})

test('hiển thị lỗi khi API lỗi', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 500 }))
  render(<App />)
  expect(await screen.findByRole('alert')).toHaveTextContent('HTTP 500')
})
