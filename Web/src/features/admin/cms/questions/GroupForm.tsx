import { useState } from 'react'
import { btnPrimary, Section, WriteError } from '../../ui'
import { send, useCmsWrite } from './api'
import { Err, Labeled, smBtn, smInput } from './fields'
import { RichText } from './RichText'
import type { GroupView } from './types'
import { uploadAudio } from './upload'
import { detailsOf, errAt } from './util'

interface Props {
  bankId: number
  group: GroupView | null
  nextSortOrder: number
  onSaved: (id: number) => void
  onDeleted: () => void
}

// Nhóm câu: bài đọc dùng chung, audio của part và script (script chỉ dùng cho Nghe ép).
export function GroupForm({ bankId, group, nextSortOrder, onSaved, onDeleted }: Props) {
  const [title, setTitle] = useState(group?.title ?? '')
  const [instruction, setInstruction] = useState(group?.instruction ?? '')
  const [passage, setPassage] = useState(group?.passageHtml ?? '')
  const [transcript, setTranscript] = useState(group?.transcriptHtml ?? '')
  const [audioId, setAudioId] = useState<number | null>(group?.audioAssetId ?? null)
  const [audioErr, setAudioErr] = useState('')
  const [uploading, setUploading] = useState(false)

  const save = useCmsWrite(() => {
    // Sửa: chuỗi rỗng = xóa trường; audioAssetId null = gỡ audio. Tạo: bỏ trường rỗng.
    const body = { title, instruction, passageHtml: passage, transcriptHtml: transcript, audioAssetId: audioId }
    return group ? send<GroupView>('PATCH', `/groups/${group.id}`, body) : send<GroupView>('POST', `/banks/${bankId}/groups`, { ...body, sortOrder: nextSortOrder })
  })
  const remove = useCmsWrite(() => send<void>('DELETE', `/groups/${group!.id}`))
  const errors = detailsOf(save.error)

  const pickAudio = async (f?: File) => {
    if (!f) return
    setAudioErr('')
    setUploading(true)
    try {
      setAudioId((await uploadAudio(f)).id)
    } catch (e) {
      setAudioErr(e instanceof Error ? e.message : 'Tải audio thất bại.')
    } finally {
      setUploading(false)
    }
  }

  return (
    <form
      aria-label="Biểu mẫu nhóm câu"
      className="flex min-w-0 flex-col gap-4 lg:col-span-2"
      onSubmit={async (e) => {
        e.preventDefault()
        const g = await save.mutateAsync().catch(() => null)
        if (g) onSaved(g.id)
      }}
    >
      <Section title={group ? 'Sửa nhóm câu' : 'Nhóm câu mới'}>
        <Labeled label="Tiêu đề (ví dụ Part 1)" htmlFor="g-title">
          <input id="g-title" maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} className={smInput} />
          <Err list={errAt(errors, 'title')} />
        </Labeled>
        <Labeled label="Hướng dẫn chung" htmlFor="g-instruction">
          <input id="g-instruction" maxLength={300} value={instruction} onChange={(e) => setInstruction(e.target.value)} className={smInput} />
          <Err list={errAt(errors, 'instruction')} />
        </Labeled>
        <RichText label="Bài đọc" value={passage} onChange={setPassage} />
        <Err list={errAt(errors, 'passageHtml')} />
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-extrabold text-ink">Audio của part</span>
          <div className="flex flex-wrap items-center gap-2">
            <label className={`${smBtn} cursor-pointer`}>
              {uploading ? 'Đang tải...' : audioId ? 'Đổi audio' : 'Tải audio lên'}
              <input type="file" hidden accept="audio/*" aria-label="Tệp audio" disabled={uploading} onChange={(e) => { void pickAudio(e.target.files?.[0]); e.target.value = '' }} />
            </label>
            {audioId !== null && (
              <>
                <span className="font-bold text-ink-secondary">Đã gắn audio.</span>
                <button type="button" className={smBtn} onClick={() => setAudioId(null)}>
                  Gỡ audio
                </button>
              </>
            )}
          </div>
          <Err list={[...(audioErr ? [audioErr] : []), ...errAt(errors, 'audioAssetId')]} />
        </div>
        <RichText label="Script của part (chỉ dùng cho Nghe ép)" value={transcript} onChange={setTranscript} />
        <Err list={errAt(errors, 'transcriptHtml')} />
      </Section>
      {save.isError && <WriteError error={save.error} />}
      {remove.isError && <WriteError error={remove.error} />}
      {save.isSuccess && !save.isPending && <p role="status" className="font-bold text-success-fg">Đã lưu.</p>}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className={btnPrimary} disabled={save.isPending || uploading}>
          Lưu nhóm
        </button>
        {group && (
          <button
            type="button"
            className={smBtn}
            onClick={async () => {
              if (!window.confirm('Xóa nhóm này?')) return
              if (await remove.mutateAsync().then(() => true, () => false)) onDeleted()
            }}
          >
            Xóa nhóm
          </button>
        )}
      </div>
    </form>
  )
}
