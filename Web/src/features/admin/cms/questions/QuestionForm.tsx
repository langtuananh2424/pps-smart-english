import { useRef, useState } from 'react'
import { ApiError } from '../../../../lib/api'
import { btnPrimary, labelCls, Section, WriteError } from '../../ui'
import { send, useCmsWrite } from './api'
import { emptyDraft, fromDetail, toRequest } from './draft'
import { Err, Labeled, smBtn, smInput } from './fields'
import { REGISTRY } from './forms/registry'
import { MediaPanel } from './MediaPanel'
import { Preview } from './Preview'
import { RichText } from './RichText'
import { QUESTION_TYPES, SKILLS, type BankView, type Draft, type GrammarElement, type GroupView, type QuestionDetail, type QuestionType } from './types'
import { uploadImage } from './upload'
import { detailsOf, errAt } from './util'

interface Props {
  bank: BankView
  question: QuestionDetail | null
  groups: GroupView[]
  elements: GrammarElement[]
  nextSortOrder: number
  onSaved: (id: number) => void
  onDeleted: () => void
}

const KNOWN = ['type', 'prompt', 'promptHtml', 'instruction', 'explanation', 'skill', 'groupId', 'choices', 'payload', 'answerKey', 'media', 'grammarElementCodes', 'transcriptExcerpt']

// Cột giữa (biểu mẫu) và cột phải (xem trước) của màn soạn; là fragment để nằm thẳng trong lưới ba cột.
export function QuestionForm({ bank, question, groups, elements, nextSortOrder, onSaved, onDeleted }: Props) {
  const [draft, setDraft] = useState<Draft>(() => (question ? fromDetail(question) : emptyDraft()))
  const savedId = useRef<number | null>(question?.id ?? null)
  const patch = (p: Partial<Draft>) => setDraft((d) => ({ ...d, ...p }))
  const { Editor } = REGISTRY[draft.type]

  const save = useCmsWrite(async () => {
    const bad = draft.media.find((m) => !m.altText.trim())
    if (bad) throw new ApiError(400, 'validation_failed', 'Dữ liệu không hợp lệ', { media: 'Cần mô tả (alt) cho mọi ảnh' })
    const body = toRequest(draft, bank)
    // Đã lưu câu mà bước ảnh lỗi thì lần thử lại sửa câu đó, không tạo câu trùng.
    const id = savedId.current
    const q = id
      ? await send<QuestionDetail>('PATCH', `/questions/${id}`, body)
      : await send<QuestionDetail>('POST', `/banks/${bank.id}/questions`, { ...body, sortOrder: nextSortOrder })
    savedId.current = q.id
    if (draft.media.length || question?.media.length) {
      await send('PUT', `/questions/${q.id}/media`, { items: draft.media.map(({ assetId, role, displaySize, altText }) => ({ assetId, role, displaySize, altText })) })
    }
    return q
  })
  const remove = useCmsWrite(() => send<void>('DELETE', `/questions/${question!.id}`))

  const errors = detailsOf(save.error)
  const changeType = (t: QuestionType) => {
    if (t === draft.type) return
    if (!window.confirm('Đổi dạng câu sẽ xóa dữ liệu riêng của dạng cũ (giữ lại đề bài). Tiếp tục?')) return
    patch({ type: t, ...REGISTRY[t].empty() })
  }
  const toggleCode = (code: string) =>
    patch({ grammarCodes: draft.grammarCodes.includes(code) ? draft.grammarCodes.filter((c) => c !== code) : [...draft.grammarCodes, code] })
  const placement = bank.purpose === 'PLACEMENT'
  const leftover = Object.entries(errors).filter(([k]) => !KNOWN.some((p) => k === p || k.startsWith(`${p}.`) || k.startsWith(`${p}[`)))

  return (
    <>
      <form
        className="flex min-w-0 flex-col gap-4"
        aria-label="Biểu mẫu câu hỏi"
        onSubmit={async (e) => {
          e.preventDefault()
          const q = await save.mutateAsync().catch(() => null)
          if (q) onSaved(q.id)
        }}
      >
        {question?.regradeRequired && (
          <p role="status" className="rounded-sm bg-reward-bg p-3 font-extrabold text-reward-fg">
            Đã có học viên làm câu này. Đổi đáp án cần chấm lại.
          </p>
        )}
        <Section title={question ? 'Sửa câu hỏi' : 'Câu hỏi mới'}>
          <Labeled label="Dạng câu" htmlFor="q-type">
            <select id="q-type" value={draft.type} onChange={(e) => changeType(e.target.value as QuestionType)} className={smInput}>
              {QUESTION_TYPES.map(([c, l]) => (
                <option key={c} value={c}>
                  {l}
                </option>
              ))}
            </select>
            <Err list={errAt(errors, 'type')} />
          </Labeled>
          <Labeled label="Hướng dẫn (hiện phía trên câu)" htmlFor="q-instruction">
            <input id="q-instruction" maxLength={300} value={draft.instruction} onChange={(e) => patch({ instruction: e.target.value })} className={smInput} />
            <Err list={errAt(errors, 'instruction')} />
          </Labeled>
          <RichText
            label="Đề bài"
            value={draft.promptHtml}
            onChange={(promptHtml) => patch({ promptHtml })}
            upload={uploadImage}
            onImage={(img) => setDraft((d) => ({ ...d, media: [...d.media, { assetId: img.id, url: img.url, role: 'INLINE', displaySize: 'MD', altText: '' }] }))}
          />
          <Err list={errAt(errors, 'prompt', 'promptHtml')} />
          <MediaPanel media={draft.media} onChange={(media) => patch({ media })} allowDiagram={draft.type === 'FILL_TEXT'} errors={errAt(errors, 'media')} />
          {groups.length > 0 && (
            <Labeled label="Nhóm câu (bài đọc / audio dùng chung)" htmlFor="q-group">
              <select id="q-group" value={draft.groupId ?? ''} onChange={(e) => patch({ groupId: e.target.value ? Number(e.target.value) : null })} className={smInput}>
                <option value="">Không thuộc nhóm</option>
                {groups.map((g, i) => (
                  <option key={g.id} value={g.id}>
                    {g.title || `Nhóm ${i + 1}`}
                  </option>
                ))}
              </select>
              <Err list={errAt(errors, 'groupId')} />
            </Labeled>
          )}
          {placement && (
            <Labeled label="Kỹ năng" htmlFor="q-skill">
              <select id="q-skill" value={draft.skill} onChange={(e) => patch({ skill: e.target.value })} className={smInput}>
                <option value="">Chọn kỹ năng</option>
                {SKILLS.map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
              <Err list={errAt(errors, 'skill')} />
            </Labeled>
          )}
        </Section>

        <Section title="Đáp án">
          <Editor draft={draft} patch={patch} errors={errors} elements={elements} />
          <Err list={errAt(errors, 'payload', 'answerKey')} />
        </Section>

        <Section title="Giải thích và thẻ">
          <Labeled label="Giải thích (hiện sau khi chấm)" htmlFor="q-explanation">
            <textarea id="q-explanation" rows={3} maxLength={5000} value={draft.explanation} onChange={(e) => patch({ explanation: e.target.value })} className={`${smInput} py-2`} />
            <Err list={errAt(errors, 'explanation')} />
          </Labeled>
          {draft.groupId !== null && (
            <Labeled label="Script riêng của câu (để trống = dùng script của nhóm)" htmlFor="q-transcript">
              <textarea id="q-transcript" rows={2} maxLength={5000} value={draft.transcriptExcerpt} onChange={(e) => patch({ transcriptExcerpt: e.target.value })} className={`${smInput} py-2`} />
              <Err list={errAt(errors, 'transcriptExcerpt')} />
            </Labeled>
          )}
          <fieldset className="flex flex-col gap-1.5">
            <legend className={labelCls}>Thẻ yếu tố ngữ pháp (tối đa 8)</legend>
            <div className="flex flex-wrap gap-2">
              {elements.map((el) => (
                <label key={el.code} className="flex min-h-9 items-center gap-1.5 rounded-sm border border-line px-2.5 font-bold text-ink">
                  <input type="checkbox" checked={draft.grammarCodes.includes(el.code)} onChange={() => toggleCode(el.code)} />
                  {el.name}
                </label>
              ))}
            </div>
            <Err list={errAt(errors, 'grammarElementCodes')} />
          </fieldset>
        </Section>

        {leftover.length > 0 && <Err list={leftover.map(([, v]) => v)} />}
        {save.isError && <WriteError error={save.error} />}
        {remove.isError && <WriteError error={remove.error} />}
        {save.isPending && <p role="status" className="font-bold text-ink-secondary">Đang lưu...</p>}
        {save.isSuccess && !save.isPending && <p role="status" className="font-bold text-success-fg">Đã lưu.</p>}
        <div className="flex flex-wrap gap-2">
          <button type="submit" className={btnPrimary} disabled={save.isPending}>
            Lưu câu hỏi
          </button>
          {question && (
            <button
              type="button"
              className={smBtn}
              onClick={async () => {
                if (!window.confirm('Xóa câu hỏi này?')) return
                if (await remove.mutateAsync().then(() => true, () => false)) onDeleted()
              }}
            >
              Xóa câu
            </button>
          )}
        </div>
      </form>
      <Preview draft={draft} elements={elements} />
    </>
  )
}
