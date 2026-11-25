import { EditorContent, useEditor, useEditorState } from '@tiptap/react'
import Image from '@tiptap/extension-image'
import { Table, TableCell, TableHeader, TableRow } from '@tiptap/extension-table'
import StarterKit from '@tiptap/starter-kit'
import { useRef, useState } from 'react'
import { labelCls } from '../../ui'
import type { UploadedImage } from './upload'

// Ảnh trong HTML trỏ tới tài sản nội bộ qua data-asset-id (backend không nhận URL ngoài).
const AssetImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      assetId: {
        default: null,
        parseHTML: (el: HTMLElement) => el.getAttribute('data-asset-id'),
        renderHTML: (a: { assetId?: string | null }) => (a.assetId ? { 'data-asset-id': a.assetId } : {}),
      },
    }
  },
})

interface Props {
  label: string
  value: string
  onChange: (html: string) => void
  // Có upload thì hiện nút chèn ảnh; onImage để chỗ gọi ghi nhận ảnh vào danh sách media của câu.
  upload?: (file: File) => Promise<UploadedImage>
  onImage?: (img: UploadedImage) => void
  compact?: boolean
}

const tb = 'inline-flex min-h-9 min-w-9 items-center justify-center rounded-sm border px-2 text-sm font-extrabold'
const body =
  'min-h-[88px] px-3 py-2 font-semibold text-ink outline-none [&_table]:border [&_td]:border [&_th]:border [&_ul]:list-disc [&_ul]:pl-5 [&_ol]:list-decimal [&_ol]:pl-5'

export function RichText({ label, value, onChange, upload, onImage, compact }: Props) {
  const [error, setError] = useState('')
  const file = useRef<HTMLInputElement>(null)
  const editor = useEditor({
    extensions: [StarterKit, AssetImage, ...(compact ? [] : [Table, TableRow, TableHeader, TableCell])],
    content: value,
    onUpdate: ({ editor: e }) => onChange(e.isEmpty ? '' : e.getHTML()),
    editorProps: { attributes: { 'aria-label': label, role: 'textbox', 'aria-multiline': 'true', class: body } },
  })
  const on = useEditorState({
    editor,
    selector: ({ editor: e }) => ({ b: !!e?.isActive('bold'), i: !!e?.isActive('italic'), u: !!e?.isActive('underline'), l: !!e?.isActive('bulletList') }),
  })

  const pick = async (f?: File) => {
    if (!f || !upload || !editor) return
    setError('')
    try {
      const img = await upload(f)
      editor.chain().focus().setImage({ src: img.url, alt: '', assetId: String(img.id) } as never).run()
      onImage?.(img)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Tải ảnh thất bại.')
    }
  }

  const btn = (name: string, text: string, active: boolean | undefined, run: () => void) => (
    <button type="button" aria-label={name} aria-pressed={active} onClick={run} className={`${tb} ${active ? 'border-brand bg-subtle text-ink' : 'border-line bg-surface text-ink-secondary'}`}>
      {text}
    </button>
  )

  return (
    <div className="flex flex-col gap-1.5">
      <span className={labelCls}>{label}</span>
      <div className="rounded-sm border-[1.5px] border-line-input bg-input focus-within:border-brand">
        {editor && (
          <div className="flex flex-wrap gap-1 border-b border-line-subtle p-1.5">
            {btn('In đậm', 'B', on?.b, () => editor.chain().focus().toggleBold().run())}
            {btn('In nghiêng', 'I', on?.i, () => editor.chain().focus().toggleItalic().run())}
            {btn('Gạch chân', 'U', on?.u, () => editor.chain().focus().toggleUnderline().run())}
            {btn('Danh sách', '•', on?.l, () => editor.chain().focus().toggleBulletList().run())}
            {!compact && btn('Chèn bảng', 'Bảng', false, () => editor.chain().focus().insertTable({ rows: 2, cols: 2, withHeaderRow: true }).run())}
            {upload && (
              <>
                {btn('Chèn ảnh', 'Ảnh', false, () => file.current?.click())}
                <input
                  ref={file}
                  type="file"
                  accept="image/jpeg,image/png,image/webp,image/gif"
                  aria-label="Tệp ảnh"
                  hidden
                  onChange={(e) => {
                    void pick(e.target.files?.[0])
                    e.target.value = ''
                  }}
                />
              </>
            )}
          </div>
        )}
        <EditorContent editor={editor} />
      </div>
      {error && (
        <p role="alert" className="text-sm font-bold text-danger-fg">
          {error}
        </p>
      )}
    </div>
  )
}
