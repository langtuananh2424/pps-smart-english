import type { Question, QuestionMedia } from './types'

// Cỡ ảnh là phần trăm khung (D-134), cùng bảng với bản xem trước ở CMS.
const SIZE_PCT: Record<string, number> = { SM: 33, MD: 50, LG: 75, FULL: 100 }

const HTML_CLS = 'break-words [&_img]:my-2 [&_img]:h-auto [&_img]:max-w-full [&_img]:rounded-md [&_table]:border [&_td]:border [&_th]:border [&_ul]:list-disc [&_ul]:pl-5 [&_ol]:list-decimal [&_ol]:pl-5'

// Đề câu: promptHtml (máy chủ đã làm sạch bằng Jsoup, src ảnh ký sẵn) nếu có, ngược lại prompt chữ thường.
export function PromptText({ question, className = '' }: { question: Pick<Question, 'prompt' | 'promptHtml'>; className?: string }) {
  if (question.promptHtml) return <div className={`${className} ${HTML_CLS}`} dangerouslySetInnerHTML={{ __html: question.promptHtml }} />
  return <div className={className}>{question.prompt}</div>
}

// Ảnh rời của câu. Ảnh INLINE đã nằm trong promptHtml (data-asset-id) thì bỏ qua để không hiện hai lần.
export function QuestionMediaList({ question }: { question: Pick<Question, 'media' | 'promptHtml'> }) {
  const shown = (question.media ?? []).filter((m) => m.role !== 'INLINE' || !question.promptHtml?.includes(`data-asset-id="${m.assetId}"`))
  if (!shown.length) return null
  return (
    <div className="flex flex-col items-center gap-2">
      {shown.map((m: QuestionMedia) => (
        <div key={m.assetId} className="w-full rounded-md border border-line bg-subtle p-2 text-center">
          <img src={m.url} alt={m.altText} width={m.width} height={m.height} loading="lazy" style={{ width: `${SIZE_PCT[m.displaySize] ?? 50}%` }} className="mx-auto h-auto max-w-full rounded-sm" />
        </div>
      ))}
    </div>
  )
}

export function QuestionStem({ question, className }: { question: Question; className: string }) {
  return (
    <>
      <PromptText question={question} className={className} />
      <QuestionMediaList question={question} />
    </>
  )
}
