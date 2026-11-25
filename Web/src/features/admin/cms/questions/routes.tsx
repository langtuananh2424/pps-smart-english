import type { RouteObject } from 'react-router'
import { BankEditorPage } from './BankEditorPage'
import { BankListPage } from './BankListPage'

// Đường dẫn tương đối, gắn dưới /quan-tri/cms (quyền cms.content do route cha kiểm).
export const bankRoutes: RouteObject[] = [
  { path: 'ngan-hang', element: <BankListPage /> },
  { path: 'ngan-hang/:bankId', element: <BankEditorPage /> },
]
