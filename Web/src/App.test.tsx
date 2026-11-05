import { render, screen } from '@testing-library/react'
import App from './App'

test('hiển thị tiêu đề', () => {
  render(<App />)
  expect(screen.getByRole('heading', { name: 'PPS Smart English' })).toBeInTheDocument()
})
