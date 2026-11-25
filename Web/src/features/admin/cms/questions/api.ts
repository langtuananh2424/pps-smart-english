import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../../../lib/api'
import type { Page } from '../../api'
import type { BankDetail, BankView, CourseTree, CourseView, GrammarElement, RuleView } from './types'

const CMS = '/admin/cms'
const KEY = ['admin', 'cms'] as const

export const send = <T,>(method: string, path: string, body?: unknown) =>
  api<T>(CMS + path, { method, body: body === undefined ? undefined : JSON.stringify(body) })

export const useCourses = () => useQuery({ queryKey: [...KEY, 'courses'], queryFn: () => api<Page<CourseView>>(`${CMS}/courses?limit=100`) })

export const useCourseTree = (id: string) =>
  useQuery({ queryKey: [...KEY, 'course', id], queryFn: () => send<CourseTree>('GET', `/courses/${id}`), enabled: !!id })

export const useBanks = (topicId: string) =>
  useQuery({ queryKey: [...KEY, 'banks', topicId], queryFn: () => send<BankView[]>('GET', `/topics/${topicId}/banks`), enabled: !!topicId })

export const useBank = (id: string) => useQuery({ queryKey: [...KEY, 'bank', id], queryFn: () => send<BankDetail>('GET', `/banks/${id}`) })

export const useGrammarElements = () =>
  useQuery({ queryKey: [...KEY, 'grammar'], queryFn: () => send<GrammarElement[]>('GET', '/grammar-elements'), staleTime: Infinity })

export const useRule = (bankId: number) =>
  useQuery({ queryKey: [...KEY, 'rule', bankId], queryFn: () => send<RuleView>('GET', `/banks/${bankId}/forced-listening`) })

// Mọi lệnh ghi CMS xong thì làm mới dữ liệu CMS.
export function useCmsWrite<A, R>(fn: (a: A) => Promise<R>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, onSuccess: () => qc.invalidateQueries({ queryKey: KEY }) })
}

export const bankWrites = {
  create: (topicId: number | string, body: object) => send<BankView>('POST', `/topics/${topicId}/banks`, body),
  patch: (id: number, body: object) => send<BankView>('PATCH', `/banks/${id}`, body),
  remove: (id: number) => send<void>('DELETE', `/banks/${id}`),
  setPublished: (id: number, published: boolean) => send<BankView>('POST', `/banks/${id}/${published ? 'publish' : 'unpublish'}`),
}
