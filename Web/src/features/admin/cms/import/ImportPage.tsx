import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { PageHead } from '../../ui'
import type { ImportInput, ImportResult, PreviewResult } from './api'
import { HistoryPanel } from './HistoryPanel'
import { InputStep } from './InputStep'
import { PreviewStep } from './PreviewStep'
import { ResultStep } from './ResultStep'

const SUBS = { input: 'Chọn cách nhập rồi dán nội dung hoặc tải file lên.', preview: 'Kiểm tra từng dòng trước khi nhập.', result: '' }

// FR-ADM-02, D-133: trình hướng dẫn nhập hàng loạt. Văn bản dán được giữ lại để sửa rồi kiểm tra lại.
export function ImportPage() {
  const { bankId = '' } = useParams()
  const [text, setText] = useState('')
  const [preview, setPreview] = useState<PreviewResult | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const step = result ? 'result' : preview ? 'preview' : 'input'

  const onPreview = (p: PreviewResult, input: ImportInput) => {
    if (input.kind === 'PASTE') setText(input.text)
    setPreview(p)
  }
  const reset = () => {
    setPreview(null)
    setResult(null)
  }
  const again = () => {
    setText('')
    reset()
  }

  return (
    <div className="flex flex-col gap-4">
      <Link to={`/quan-tri/cms/ngan-hang/${bankId}`} className="min-h-11 self-start font-extrabold text-brand-strong underline">
        Về bộ câu hỏi
      </Link>
      <PageHead title="Nhập câu hỏi" sub={SUBS[step]} />
      {step === 'input' && <InputStep bankId={bankId} text={text} onText={setText} onPreview={onPreview} />}
      {step === 'preview' && preview && <PreviewStep bankId={bankId} preview={preview} onBack={reset} onDone={setResult} />}
      {step === 'result' && result && <ResultStep bankId={bankId} result={result} onAgain={again} />}
      <HistoryPanel bankId={bankId} />
    </div>
  )
}
