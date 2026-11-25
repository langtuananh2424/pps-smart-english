import { REGISTRY } from './forms/registry'
import { usesChoices, type BankView, type Draft, type QuestionDetail, type QuestionType } from './types'
import { hydrateImages, isEmptyHtml, stripImgSrc, textToHtml, uid } from './util'

export function emptyDraft(type: QuestionType = 'CONNECTIVE_MCQ'): Draft {
  return {
    type,
    promptHtml: '',
    instruction: '',
    explanation: '',
    groupId: null,
    skill: '',
    transcriptExcerpt: '',
    grammarCodes: [],
    media: [],
    ...REGISTRY[type].empty(),
  }
}

export function fromDetail(q: QuestionDetail): Draft {
  const empty = REGISTRY[q.type].empty()
  return {
    type: q.type,
    promptHtml: hydrateImages(q.promptHtml ?? textToHtml(q.prompt), q.media),
    instruction: q.instruction ?? '',
    explanation: q.explanation ?? '',
    groupId: q.groupId,
    skill: q.skill ?? '',
    transcriptExcerpt: q.transcriptExcerpt ?? '',
    grammarCodes: q.grammarElementCodes,
    media: q.media.map((m) => ({ assetId: m.assetId, url: m.url, role: m.role, displaySize: m.displaySize, altText: m.altText })),
    payload: q.payload ?? empty.payload,
    answerKey: q.answerKey ?? empty.answerKey,
    choices: usesChoices(q.type)
      ? q.choices.map((c) => ({ uid: uid(), label: c.label, contentHtml: c.contentHtml ?? textToHtml(c.content), isCorrect: c.isCorrect, explanation: c.explanation ?? '' }))
      : [],
  }
}

// Object rỗng nghĩa là "không có": backend coi {} như vắng mặt, ta gửi null cho rõ.
const norm = (o: object) => (Object.keys(o).length ? o : null)

// Cùng một body cho POST và PATCH. groupId null = không thuộc nhóm; transcriptExcerpt '' = dùng script của nhóm.
export function toRequest(d: Draft, bank: BankView) {
  return {
    type: d.type,
    promptHtml: isEmptyHtml(d.promptHtml) ? undefined : stripImgSrc(d.promptHtml),
    instruction: d.instruction,
    explanation: d.explanation,
    groupId: d.groupId,
    payload: norm(d.payload),
    answerKey: norm(d.answerKey),
    choices: usesChoices(d.type)
      ? d.choices.map((c) => ({ label: c.label, contentHtml: isEmptyHtml(c.contentHtml) ? undefined : stripImgSrc(c.contentHtml), isCorrect: c.isCorrect, explanation: c.explanation }))
      : [],
    grammarElementCodes: d.grammarCodes,
    // Kỹ năng chỉ dùng cho đề kiểm tra đầu vào; bộ khóa học bị backend từ chối.
    skill: bank.purpose === 'PLACEMENT' && d.skill ? d.skill : undefined,
    transcriptExcerpt: d.transcriptExcerpt,
  }
}
