import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { btnOutline, Loading, RetryError, WriteError } from '../../ui'
import { bankWrites, useBank, useCmsWrite, useGrammarElements } from './api'
import { BankForm } from './BankForm'
import { Pill, smBtn } from './fields'
import { ForcedListeningPanel } from './ForcedListeningPanel'
import { GroupForm } from './GroupForm'
import { QuestionForm } from './QuestionForm'
import { QuestionList, type Selection } from './QuestionList'
import type { BankDetail } from './types'

function Editor({ detail }: { detail: BankDetail }) {
  const { bank, questions, groups } = detail
  const navigate = useNavigate()
  const elements = useGrammarElements().data ?? []
  const [showProps, setShowProps] = useState(false)
  const [nonce, setNonce] = useState(0)
  const first = [...questions].sort((a, b) => a.sortOrder - b.sortOrder)[0]
  const [sel, setSel] = useState<Selection>(first ? { k: 'q', id: first.id } : { k: 'newQ' })
  const toggle = useCmsWrite(() => bankWrites.setPublished(bank.id, bank.status !== 'PUBLISHED'))
  const live = bank.status === 'PUBLISHED'
  const select = (s: Selection) => {
    setNonce((n) => n + 1)
    setSel(s)
  }
  const question = sel.k === 'q' ? questions.find((q) => q.id === sel.id) : undefined
  const group = sel.k === 'g' ? groups.find((g) => g.id === sel.id) : undefined
  const nextSort = questions.reduce((m, q) => Math.max(m, q.sortOrder + 1), 0)

  return (
    <div className="flex flex-col gap-4">
      <header className="flex flex-wrap items-center gap-3">
        <button type="button" className={smBtn} onClick={() => navigate(-1)}>
          ← Quay lại
        </button>
        <h1 className="min-w-0 flex-1 truncate text-2xl font-black text-ink">{bank.title}</h1>
        <Pill tone={live ? 'live' : 'draft'}>{live ? 'Đã xuất bản' : 'Bản nháp'}</Pill>
        <button type="button" className={smBtn} onClick={() => setShowProps((v) => !v)}>
          Thuộc tính bộ
        </button>
        <Link to={`/quan-tri/cms/ngan-hang/${bank.id}/nhap-hang-loat`} className={btnOutline}>
          Nhập hàng loạt
        </Link>
        <button type="button" className={smBtn} disabled={toggle.isPending} onClick={() => toggle.mutate()}>
          {live ? 'Ngừng xuất bản' : 'Xuất bản'}
        </button>
      </header>
      {toggle.isError && <WriteError error={toggle.error} />}
      {showProps && <BankForm key={bank.id} bank={bank} onDone={() => setShowProps(false)} onCancel={() => setShowProps(false)} />}
      {bank.playbackMode === 'LOCKED' && <ForcedListeningPanel bankId={bank.id} groups={groups} />}

      <div className="grid items-start gap-4 lg:grid-cols-[240px_minmax(0,1fr)_320px]">
        <QuestionList questions={questions} groups={groups} sel={sel} onSelect={select} />
        {sel.k === 'g' || sel.k === 'newG' ? (
          <GroupForm
            key={`g${sel.k === 'g' ? sel.id : nonce}`}
            bankId={bank.id}
            group={group ?? null}
            nextSortOrder={groups.length}
            onSaved={(id) => select({ k: 'g', id })}
            onDeleted={() => select({ k: 'newQ' })}
          />
        ) : (
          <QuestionForm
            key={`q${sel.k === 'q' ? sel.id : `new${nonce}`}`}
            bank={bank}
            question={question ?? null}
            groups={groups}
            elements={elements}
            nextSortOrder={nextSort}
            onSaved={(id) => (sel.k === 'q' ? undefined : select({ k: 'q', id }))}
            onDeleted={() => select({ k: 'newQ' })}
          />
        )}
      </div>
    </div>
  )
}

// FR-ADM-02, FR-EX-04/05: màn soạn bộ câu hỏi ba cột (danh sách câu, biểu mẫu, xem trước).
export function BankEditorPage() {
  const { bankId = '' } = useParams()
  const q = useBank(bankId)
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />
  return <Editor key={bankId} detail={q.data} />
}
