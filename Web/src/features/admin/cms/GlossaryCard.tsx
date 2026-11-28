import { useId, useState } from 'react'
import { inputCls, labelCls, Loading, RetryError, Section, WriteError, btnOutline, btnPrimary } from '../ui'
import { cmsWrites, searchDictionary, useCmsWrite, useGlossary, type DictionaryHit, type GlossaryBody, type GlossaryItem } from './api'
import { DeleteButton } from './DeleteButton'
import { small } from './NodeRow'

const blank = { phrase: '', partOfSpeech: '', meaningVi: '', example: '' }

function GlossaryForm({ item, onSave, onCancel }: { item?: GlossaryItem; onSave: (b: GlossaryBody) => Promise<unknown>; onCancel?: () => void }) {
  const uid = useId()
  const [v, setV] = useState(item ? { phrase: item.phrase, partOfSpeech: item.partOfSpeech ?? '', meaningVi: item.meaningVi, example: item.example ?? '' } : blank)
  const [source, setSource] = useState<'MANUAL' | 'WIKTIONARY'>(item?.source ?? 'MANUAL')
  const [hits, setHits] = useState<DictionaryHit[] | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof blank, manual = false) => (e: { target: { value: string } }) => {
    setV((o) => ({ ...o, [k]: e.target.value }))
    if (manual) setSource('MANUAL') // tự sửa nghĩa thì không còn là nghĩa lấy từ từ điển
  }
  const suggest = async () => {
    setError(null)
    try {
      setHits(await searchDictionary(v.phrase.trim()))
    } catch (e) {
      setError(e)
    }
  }
  const pick = (h: DictionaryHit) => {
    setV((o) => ({ ...o, partOfSpeech: h.partOfSpeech ?? '', meaningVi: h.meaningVi, example: h.example ?? '' }))
    setSource('WIKTIONARY')
    setHits(null)
  }
  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (busy) return
    setError(null)
    setBusy(true)
    try {
      await onSave({ ...v, source })
      if (!item) {
        setV(blank)
        setSource('MANUAL')
      }
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }
  const field = (k: keyof typeof blank, label: string, extra: { max: number; manual?: boolean }) => (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={`${uid}-${k}`} className={labelCls}>
        {label}
      </label>
      <input id={`${uid}-${k}`} value={v[k]} maxLength={extra.max} onChange={set(k, extra.manual)} className={inputCls} />
    </div>
  )
  return (
    <form onSubmit={submit} aria-label={item ? 'Sửa mục giải nghĩa' : 'Thêm mục giải nghĩa'} className="flex flex-col gap-3 rounded-md border border-line-brand bg-page p-3">
      {field('phrase', 'Từ hoặc cụm từ', { max: 100 })}
      <div className="flex flex-wrap items-center gap-2">
        <button type="button" className={small} disabled={!v.phrase.trim()} onClick={suggest}>
          Gợi ý nghĩa
        </button>
        {hits?.length === 0 && <span className="text-sm font-semibold text-ink-secondary">Không có gợi ý.</span>}
      </div>
      {hits && hits.length > 0 && (
        <ul aria-label="Gợi ý từ từ điển" className="flex flex-col gap-1">
          {hits.map((h, i) => (
            <li key={i}>
              <button type="button" onClick={() => pick(h)} className="min-h-11 w-full rounded-md border border-line-brand bg-surface px-3 text-left text-sm font-bold text-ink">
                {h.word}
                {h.partOfSpeech ? ` (${h.partOfSpeech})` : ''} · {h.meaningVi}
              </button>
            </li>
          ))}
        </ul>
      )}
      {field('partOfSpeech', 'Từ loại', { max: 30 })}
      {field('meaningVi', 'Nghĩa tiếng Việt', { max: 500, manual: true })}
      {field('example', 'Ví dụ', { max: 500 })}
      {error != null && <WriteError error={error} />}
      <div className="flex flex-wrap gap-2">
        <button type="submit" disabled={busy} className={btnPrimary}>
          {item ? 'Lưu mục' : 'Thêm mục'}
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel} className={btnOutline}>
            Hủy
          </button>
        )}
      </div>
    </form>
  )
}

function Item({ item }: { item: GlossaryItem }) {
  const [editing, setEditing] = useState(false)
  const patch = useCmsWrite((b: GlossaryBody) => cmsWrites.patchGlossary(item.id, b))
  const del = useCmsWrite(() => cmsWrites.deleteGlossary(item.id))
  if (editing)
    return (
      <GlossaryForm
        item={item}
        onSave={async (b) => {
          await patch.mutateAsync(b)
          setEditing(false)
        }}
        onCancel={() => setEditing(false)}
      />
    )
  return (
    <li className="flex flex-col gap-2 rounded-md border border-line-brand bg-surface p-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="break-words font-extrabold text-ink">
          {item.phrase}
          {item.partOfSpeech && <span className="font-semibold text-ink-secondary"> ({item.partOfSpeech})</span>}
          {item.source === 'WIKTIONARY' && <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-xs font-bold text-ink-secondary">Wiktionary</span>}
        </span>
        <span className="break-words font-semibold text-ink">{item.meaningVi}</span>
        {item.example && <span className="break-words text-sm font-semibold text-ink-secondary">{item.example}</span>}
      </div>
      <div className="flex flex-wrap gap-2">
        <button type="button" aria-label={`Sửa ${item.phrase}`} className={small} onClick={() => setEditing(true)}>
          Sửa
        </button>
        <DeleteButton label={item.phrase} pending={del.isPending} onConfirm={() => del.mutate(undefined)} />
      </div>
      {del.isError && <WriteError error={del.error} onRetry={() => del.reset()} />}
    </li>
  )
}

// FR-LMS-02, D-173: từ nối và giải nghĩa gắn với bài giảng.
export function GlossaryCard({ lessonId }: { lessonId: number }) {
  const q = useGlossary(lessonId)
  const add = useCmsWrite((b: GlossaryBody) => cmsWrites.addGlossary(lessonId, b))
  return (
    <Section title="Từ nối và giải nghĩa">
      {q.isPending ? (
        <Loading />
      ) : q.isError ? (
        <RetryError error={q.error} onRetry={() => q.refetch()} />
      ) : (
        <>
          {q.data.length === 0 && <p className="font-semibold text-ink-secondary">Chưa có mục nào.</p>}
          <ul className="flex flex-col gap-2">
            {q.data.map((g) => (
              <Item key={g.id} item={g} />
            ))}
          </ul>
        </>
      )}
      <GlossaryForm onSave={(b) => add.mutateAsync(b)} />
    </Section>
  )
}
