import type { RouteObject } from 'react-router'
import { CourseContentPage } from './CourseContentPage'
import { CourseCreatePage } from './CourseCreatePage'
import { CourseSettingsPage } from './CourseSettingsPage'
import { CoursesPage } from './CoursesPage'
import { LessonEditPage, PublishedLessonPage } from './LessonPages'

// FR-ADM-01: route con của /quan-tri/cms (quyền cms.content kiểm ở routes.tsx của admin).
export const cmsRoutes: RouteObject[] = [
  { path: 'cms/khoa-hoc', element: <CoursesPage /> },
  { path: 'cms/khoa-hoc/moi', element: <CourseCreatePage /> },
  { path: 'cms/khoa-hoc/:courseId', element: <CourseContentPage /> },
  { path: 'cms/khoa-hoc/:courseId/cau-hinh', element: <CourseSettingsPage /> },
  { path: 'cms/khoa-hoc/:courseId/bai-giang/:lessonId/da-xuat-ban', element: <PublishedLessonPage /> },
  { path: 'cms/khoa-hoc/:courseId/bai-giang/:lessonId', element: <LessonEditPage /> },
]
