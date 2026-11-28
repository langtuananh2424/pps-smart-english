import type { RouteObject } from 'react-router'
import { AssignPage } from './AssignPage'
import { AllAssignmentsPage, ClassAssignmentsPage } from './AssignmentsPage'
import { ClassesPage } from './ClassesPage'
import { GradePage } from './GradePage'
import { ProgressPage } from './ProgressPage'
import { StudentProfilePage } from './StudentProfilePage'
import { SubmissionsPage } from './SubmissionsPage'
import { TeachingShell } from './TeachingShell'
import { UnlockPage } from './UnlockPage'

// #61, #62: khu giảng dạy. Quyền theo lớp do API kiểm (người ngoài lớp nhận 404, NFR-SEC-05).
export const teachingRoutes: RouteObject = {
  path: '/giang-day',
  element: <TeachingShell />,
  children: [
    { index: true, element: <ClassesPage /> },
    { path: 'bai-giao', element: <AllAssignmentsPage /> },
    { path: 'lop/:classId', element: <ProgressPage /> },
    { path: 'lop/:classId/hoc-vien/:userId', element: <StudentProfilePage /> },
    { path: 'lop/:classId/mo-unit', element: <UnlockPage /> },
    { path: 'lop/:classId/giao-bai', element: <AssignPage /> },
    { path: 'lop/:classId/bai-giao', element: <ClassAssignmentsPage /> },
    { path: 'lop/:classId/bai-giao/:assignmentId', element: <SubmissionsPage /> },
    { path: 'lop/:classId/bai-giao/:assignmentId/bai-nop/:attemptId', element: <GradePage /> },
  ],
}
