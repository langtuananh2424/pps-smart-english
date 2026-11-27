import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { emptyDraft } from '../draft'
import type { Draft } from '../types'
import { REGISTRY } from './registry'

let last: Draft

function Harness() {
  const [draft, setDraft] = useState(() => emptyDraft('LETTER_ORDER'))
  last = draft
  const { Editor } = REGISTRY.LETTER_ORDER
  return <Editor draft={draft} patch={(p) => setDraft((d) => ({ ...d, ...p }))} errors={{}} elements={[]} layout={null} />
}

// D-154 (tester): payload.letters trả nguyên cho học viên (BankService không xáo LETTER_ORDER), nên id khối chữ không được lộ thứ tự đúng.
describe('LETTER_ORDER không lộ đáp án qua id', () => {
  test('xếp khối chữ theo id không ra từ đúng', async () => {
    render(<Harness />)
    await userEvent.type(screen.getByLabelText('Từ đúng'), 'keyboard')
    const letters = last.payload.letters as { id: string; ch: string }[]
    const num = (id: string) => Number(id.replace(/\D/g, ''))
    const byId = [...letters].sort((a, b) => num(a.id) - num(b.id) || a.id.localeCompare(b.id)).map((l) => l.ch).join('')
    expect(letters.map((l) => l.ch).join('')).not.toBe('keyboard') // đã xáo
    expect(byId).not.toBe('keyboard')
  })
})
