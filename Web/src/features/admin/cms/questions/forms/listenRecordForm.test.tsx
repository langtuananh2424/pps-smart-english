import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ListenRecordEditor } from './listenRecordForm'
import type { Draft } from '../types'

test('nhập đề và thời gian; để trống thời gian thì bỏ khỏi payload', async () => {
  const patch = vi.fn()
  const draft = { payload: { prompt: 'a', maxSeconds: 30 }, answerKey: {}, choices: [] } as unknown as Draft
  render(<ListenRecordEditor draft={draft} patch={patch} errors={{}} elements={[]} layout={null} />)
  await userEvent.type(screen.getByLabelText('Đề'), 'b')
  expect(patch).toHaveBeenLastCalledWith({ payload: { prompt: 'ab', maxSeconds: 30 } })
  await userEvent.clear(screen.getByLabelText(/Thời gian tối đa/))
  expect(patch).toHaveBeenLastCalledWith({ payload: { prompt: 'a' } })
})
