import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../../lib/api'
import type { Page } from '../api'

export type Status = 'DRAFT' | 'PUBLISHED'
export type Kind = 'courses' | 'levels' | 'topics' | 'lessons'
export const SKILLS = ['LISTENING', 'READING', 'WRITING', 'SPEAKING', 'GRAMMAR_VOCAB'] as const
export const LESSON_TYPES = ['CONNECTIVE_VIDEO', 'REFLEX_VIDEO'] as const

export interface CourseView {
  id: number
  slug: string
  title: string
  description: string | null
  targetType: string | null
  status: Status
  sequential: boolean
  ielts: boolean // D-161
  lessonCompletePercent: number
  examUnlockPercent: number
  examRetakes: number
  deliveryMode: string
}
export interface LevelView { id: number; courseId: number; code: string; title: string; sortOrder: number; status: Status }
export interface TopicView { id: number; levelId: number; title: string; sortOrder: number; isFreeTrial: boolean; skill: string | null; status: Status }
export interface LessonView {
  id: number
  topicId: number
  type: string
  title: string
  durationSec: number | null
  sortOrder: number
  status: Status
  videoSource: string | null
  videoRef: string | null
  videoUrl?: string | null // D-171: URL ký để xem thử video tải lên
  subtitles?: SubtitleInfo[]
}
export interface SubtitleInfo { lang: SubtitleLang; cueCount: number; updatedAt: string }
export type SubtitleLang = 'en' | 'vi'
export interface GlossaryItem { id: number; phrase: string; partOfSpeech: string | null; meaningVi: string; example: string | null; source: 'MANUAL' | 'WIKTIONARY' }
export type GlossaryBody = Partial<Omit<GlossaryItem, 'id'>>
export interface DictionaryHit { word: string; partOfSpeech: string | null; meaningVi: string; example: string | null }
export interface TopicNode { topic: TopicView; lessons: LessonView[] }
export interface LevelNode { level: LevelView; topics: TopicNode[] }
export interface CourseTree { course: CourseView; levels: LevelNode[] }

const BASE = '/admin/cms'
const KEY = ['admin', 'cms'] as const

export const useCourses = () =>
  useInfiniteQuery({
    queryKey: [...KEY, 'courses'],
    queryFn: ({ pageParam }) => api<Page<CourseView>>(`${BASE}/courses?limit=20${pageParam ? `&cursor=${encodeURIComponent(pageParam)}` : ''}`),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })

export const useCourseTree = (id: string) =>
  useQuery({ queryKey: [...KEY, 'tree', id], queryFn: () => api<CourseTree>(`${BASE}/courses/${id}`) })

const send = <T>(method: string, path: string, body?: unknown) =>
  api<T>(`${BASE}${path}`, { method, body: body === undefined ? undefined : JSON.stringify(body) })

export const cmsWrites = {
  createCourse: (b: { slug: string; title: string; description?: string; targetType?: string }) => send<CourseView>('POST', '/courses', b),
  patchCourse: (id: number, b: Partial<Omit<CourseView, 'id' | 'status'>>) => send<CourseView>('PATCH', `/courses/${id}`, b),
  create: (parent: `${'courses' | 'levels' | 'topics'}/${number}`, child: 'levels' | 'topics' | 'lessons', b: object) =>
    send<unknown>('POST', `/${parent}/${child}`, b),
  patch: (kind: Exclude<Kind, 'courses'>, id: number, b: object) => send<unknown>('PATCH', `/${kind}/${id}`, b),
  remove: (kind: Kind, id: number) => send<void>('DELETE', `/${kind}/${id}`),
  setPublished: (kind: Kind, id: number, published: boolean) => send<unknown>('POST', `/${kind}/${id}/${published ? 'publish' : 'unpublish'}`),
  // Phụ đề gửi multipart nên không qua send() (JSON).
  putSubtitle: (lessonId: number, lang: SubtitleLang, form: FormData) => api<SubtitleInfo>(`${BASE}/lessons/${lessonId}/subtitles/${lang}`, { method: 'PUT', body: form }),
  deleteSubtitle: (lessonId: number, lang: SubtitleLang) => send<void>('DELETE', `/lessons/${lessonId}/subtitles/${lang}`),
  addGlossary: (lessonId: number, b: GlossaryBody) => send<GlossaryItem>('POST', `/lessons/${lessonId}/glossary`, b),
  patchGlossary: (id: number, b: GlossaryBody) => send<GlossaryItem>('PATCH', `/glossary/${id}`, b),
  deleteGlossary: (id: number) => send<void>('DELETE', `/glossary/${id}`),
}

export const useGlossary = (lessonId: number) =>
  useQuery({ queryKey: [...KEY, 'glossary', lessonId], queryFn: () => api<GlossaryItem[]>(`${BASE}/lessons/${lessonId}/glossary`) })

export const searchDictionary = (q: string) => api<DictionaryHit[]>(`${BASE}/dictionary?q=${encodeURIComponent(q)}`)

// Mọi lệnh ghi CMS xong thì làm mới danh sách và cây khóa học.
export function useCmsWrite<V, R>(fn: (v: V) => Promise<R>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, onSuccess: () => qc.invalidateQueries({ queryKey: KEY }) })
}
