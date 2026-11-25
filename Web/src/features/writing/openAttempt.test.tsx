import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { ApiError } from '../../lib/api'
import { BlockingError as SpeakingBlocking } from '../speaking/errors'
import { BlockingError } from './errors'

// D-147: câu đang nằm trong bài chưa nộp thì không luyện riêng, dẫn về bài đó.
const error = new ApiError(409, 'question_in_open_attempt', 'x', { attemptId: 9, bankId: 7 })

describe('câu đang nằm trong lượt mở', () => {
  test.each([['Viết', BlockingError], ['Nói', SpeakingBlocking]])('màn %s báo và dẫn về bài đang làm', (_name, Block) => {
    render(<MemoryRouter><Block error={error} /></MemoryRouter>)
    expect(screen.getByText('Câu này đang nằm trong bài bạn chưa nộp')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Tiếp tục bài làm' })).toHaveAttribute('href', '/bai-tap/7')
  })
})
