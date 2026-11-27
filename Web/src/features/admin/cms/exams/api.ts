import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../../../lib/api'

export interface Bank {
  id: number
  topicId: number | null
  lessonId: number | null
  title: string
  status: 'DRAFT' | 'PUBLISHED'
  questionCount: number
  timeLimitSec: number | null
  mode: string
  purpose: string
}
export interface BankQuestion {
  id: number
  type: string
  prompt: string
  skill: string
  sortOrder: number
}
export interface BankDetail {
  bank: Bank
  questions: BankQuestion[]
}
export interface Course {
  id: number
  title: string
  examBankId: number | null
  examUnlockPercent: number
  examRetakes: number
}
export interface Topic {
  id: number
  title: string
  chapterTestBankId: number | null
  chapterTestRetakes: number
}
export interface CourseTree {
  course: Course
  levels: { topics: { topic: Topic }[] }[]
}
export interface BandRule {
  skill: string
  minPercent: number
  band: number
}

export const SKILLS = ['LISTENING', 'READING', 'GRAMMAR'] as const
// Bảng band đề thi khóa (D-167) thêm Viết, Nói; bảng đề đầu vào chỉ ba kỹ năng đầu.
export const BANK_SKILLS = ['LISTENING', 'READING', 'GRAMMAR', 'WRITING', 'SPEAKING'] as const
export const SKILL_LABEL: Record<string, string> = { LISTENING: 'Nghe', READING: 'Đọc', GRAMMAR: 'Ngữ pháp', WRITING: 'Viết', SPEAKING: 'Nói' }
export const isPlacement = (b: Bank) => b.purpose === 'PLACEMENT'

const BASE = '/admin/cms'
const KEY = 'cms-exams'
const call = <T>(method: string, path: string, body?: unknown) =>
  api<T>(BASE + path, { method, body: body === undefined ? undefined : JSON.stringify(body) })

export const usePlacementBanks = () => useQuery({ queryKey: [KEY, 'placement'], queryFn: () => call<Bank[]>('GET', '/placement/banks') })
export const useBankDetail = (id: string) => useQuery({ queryKey: [KEY, 'bank', id], queryFn: () => call<BankDetail>('GET', `/banks/${id}`) })
export const usePlacementConfig = () =>
  useQuery({ queryKey: [KEY, 'config'], queryFn: () => call<{ maxRetakes: number | null }>('GET', '/placement/config') })
export const useBandRules = () => useQuery({ queryKey: [KEY, 'rules'], queryFn: () => call<BandRule[]>('GET', '/placement/band-rules') })
export const useBankBandRules = (id: number) =>
  useQuery({ queryKey: [KEY, 'bank-rules', id], queryFn: () => call<BandRule[]>('GET', `/banks/${id}/band-rules`) })
// Một trang tối đa 100 khóa; shortcut: chưa phân trang tiếp, khi vượt 100 khóa thì thêm tìm kiếm.
export const useCourses = () =>
  useQuery({ queryKey: [KEY, 'courses'], queryFn: () => call<{ data: Course[] }>('GET', '/courses?limit=100'), select: (d) => d.data })
export const useCourseTree = (id: string) =>
  useQuery({ queryKey: [KEY, 'tree', id], queryFn: () => call<CourseTree>('GET', `/courses/${id}`), enabled: !!id })

// Đề thi khóa = bộ mode EXAM của từng topic (BE không có API liệt kê theo khóa).
export function useTopicBanks(topicIds: number[]) {
  const results = useQueries({
    queries: topicIds.map((id) => ({ queryKey: [KEY, 'topic-banks', id], queryFn: () => call<Bank[]>('GET', `/topics/${id}/banks`) })),
  })
  return {
    pending: results.some((r) => r.isPending),
    error: results.find((r) => r.isError)?.error,
    banks: results.flatMap((r) => r.data ?? []).filter((b) => b.mode === 'EXAM'),
  }
}

export const examApi = {
  createPlacement: (b: { title: string; timeLimitSec: number }) => call<Bank>('POST', '/placement/banks', b),
  patchBank: (id: number, b: { title?: string; timeLimitSec?: number }) => call<Bank>('PATCH', `/banks/${id}`, b),
  setPublished: (bank: Bank, published: boolean) =>
    call<Bank>('POST', `${isPlacement(bank) ? '/placement' : ''}/banks/${bank.id}/${published ? 'publish' : 'unpublish'}`),
  patchCourse: (id: number, b: Record<string, unknown>) => call<Course>('PATCH', `/courses/${id}`, b),
  patchTopic: (id: number, b: Record<string, unknown>) => call<Topic>('PATCH', `/topics/${id}`, b),
  putConfig: (maxRetakes: number | null) => call<{ maxRetakes: number | null }>('PUT', '/placement/config', { maxRetakes }),
  putBankRules: (id: number, rules: BandRule[]) => call<BandRule[]>('PUT', `/banks/${id}/band-rules`, { rules }),
  putRules: (rules: BandRule[]) => call<BandRule[]>('PUT', '/placement/band-rules', { rules }),
}

// Mọi lệnh ghi xong thì làm mới dữ liệu của màn đề thi.
export function useExamWrite<V, T>(fn: (v: V) => Promise<T>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }) })
}
