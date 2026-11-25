import type { RouteObject } from 'react-router'
import { BandRulesPage } from './BandRulesPage'
import { ExamEditPage } from './ExamEditPage'
import { ExamListPage } from './ExamListPage'

// FR-ADM-06: route tương đối, gắn dưới cms (cms.content) bởi routes của admin.
export const examRoutes: RouteObject[] = [
  { path: 'de-thi', element: <ExamListPage /> },
  { path: 'de-thi/quy-doi', element: <BandRulesPage /> },
  { path: 'de-thi/:bankId', element: <ExamEditPage /> },
]
