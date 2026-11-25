import type { RouteObject } from 'react-router'
import { ImportPage } from './ImportPage'

// Đường dẫn tương đối, gắn dưới /quan-tri/cms (quyền cms.content do route cha kiểm).
export const importRoutes: RouteObject[] = [{ path: 'ngan-hang/:bankId/nhap-hang-loat', element: <ImportPage /> }]
