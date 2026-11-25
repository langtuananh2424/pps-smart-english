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
}
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
}

// Mọi lệnh ghi CMS xong thì làm mới danh sách và cây khóa học.
export function useCmsWrite<V, R>(fn: (v: V) => Promise<R>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, onSuccess: () => qc.invalidateQueries({ queryKey: KEY }) })
}
