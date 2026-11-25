import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api, postJson, rawFetch } from '../../../../lib/api'

// FR-ADM-02, D-133: nhập hàng loạt câu hỏi; hình dạng khớp ImportDtos của backend.
export interface RowError {
  field: string | null
  message: string
}
export interface Duplicate {
  scope: 'FILE' | 'BANK' | 'TOPIC'
  questionId: number | null
  bankId: number | null
  bankTitle: string | null
  line: number | null
}
export interface PreviewRow {
  sheet: string
  line: number
  type: string | null
  prompt: string | null
  valid: boolean
  errors: RowError[]
  duplicates: Duplicate[]
  question: unknown
}
export interface PreviewResult {
  source: 'PASTE' | 'EXCEL'
  total: number
  valid: number
  invalid: number
  rows: PreviewRow[]
  warnings: string[]
}
export interface SkippedRow {
  line: number
  errors: RowError[]
}
export interface ImportResult {
  batchId: number
  imported: number
  skipped: SkippedRow[]
}
export interface BatchView {
  id: number
  bankId: number
  userId: number
  source: string
  status: string
  questionCount: number
  createdAt: string
  undoneAt: string | null
}
export interface UndoResult {
  batchId: number
  deleted: number
  archived: number
}
export type ImportMode = 'ALL_OR_NOTHING' | 'SKIP_INVALID'
export type ImportInput = { kind: 'PASTE'; text: string } | { kind: 'EXCEL'; file: File }

export const MAX_FILE_BYTES = 2 * 1024 * 1024
const base = (bankId: string) => `/admin/cms/banks/${bankId}`

export const previewImport = (bankId: string, input: ImportInput) => {
  const form = new FormData()
  if (input.kind === 'PASTE') form.set('text', input.text)
  else form.set('file', input.file)
  return api<PreviewResult>(`${base(bankId)}/imports/preview`, { method: 'POST', body: form })
}

export const confirmImport = (bankId: string, source: string, mode: ImportMode, rows: { line: number; question: unknown }[]) =>
  postJson<ImportResult>(`${base(bankId)}/imports`, { source, mode, rows })

const historyKey = (bankId: string) => ['admin', 'cms', 'import-history', bankId]

export const useImportHistory = (bankId: string) =>
  useQuery({ queryKey: historyKey(bankId), queryFn: () => api<BatchView[]>(`${base(bankId)}/imports`) })

export function useUndoImport(bankId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (batchId: number) => api<UndoResult>(`/admin/cms/imports/${batchId}/undo`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: historyKey(bankId) }),
  })
}

export const useConfirmImport = (bankId: string) => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { source: string; mode: ImportMode; rows: { line: number; question: unknown }[] }) =>
      confirmImport(bankId, v.source, v.mode, v.rows),
    onSuccess: () => qc.invalidateQueries({ queryKey: historyKey(bankId) }),
  })
}

// Phản hồi không phải JSON (markdown, xlsx): `api` không đọc được nên gọi thô, vẫn kèm Bearer.
async function rawGet(path: string): Promise<Response> {
  const res = await rawFetch(path)
  if (!res.ok) throw new ApiError(res.status, 'UNKNOWN', res.status === 403 ? 'Bạn không có quyền thực hiện thao tác này.' : 'Không tải được, vui lòng thử lại.')
  return res
}

export const fetchGuide = () => rawGet('/admin/cms/import/guide').then((r) => r.text())

export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export const TEMPLATE_TYPES: { type: string; label: string }[] = [
  { type: '', label: 'Tất cả dạng' },
  { type: 'CONNECTIVE_MCQ', label: 'Trắc nghiệm' },
  { type: 'CONNECTIVE_MATCH', label: 'Ghép hai vế' },
  { type: 'CONNECTIVE_CLOZE', label: 'Điền từ' },
  { type: 'GRAMMAR_BUILD', label: 'Xếp khối' },
]

export async function downloadTemplate(type: string) {
  const res = await rawGet(`/admin/cms/import/templates.xlsx${type ? `?type=${type}` : ''}`)
  saveBlob(await res.blob(), `mau-nhap-${type ? type.toLowerCase() : 'tat-ca'}.xlsx`)
}

// BE không có file báo cáo lỗi: tự xuất CSV (BOM để Excel đọc đúng tiếng Việt).
export function errorReportCsv(rows: { line: number; sheet?: string; errors: RowError[] }[]) {
  const cell = (s: string) => `"${s.replace(/"/g, '""')}"`
  const lines = rows.flatMap((r) => r.errors.map((e) => [String(r.line), r.sheet ?? '', e.field ?? '', e.message].map(cell).join(',')))
  return '﻿' + ['Dòng,Sheet,Cột,Lỗi', ...lines].join('\r\n')
}
