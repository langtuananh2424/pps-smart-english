import type { RouteObject } from 'react-router'
import { PublicOnly, RequireAuth } from '../auth/guards'
import { ConfirmPage } from '../features/checkout/ConfirmPage'
import { OrderPage } from '../features/checkout/OrderPage'
import { OrdersPage } from '../features/checkout/OrdersPage'
import { GoalPage } from '../features/goals/GoalPage'
import { StreakPage } from '../features/goals/StreakPage'
import { CoursesPage } from '../features/courses/CoursesPage'
import { CoursePage } from '../features/lectures/CoursePage'
import { LessonPage } from '../features/lectures/LessonPage'
import { PickerPage } from '../features/lectures/PickerPage'
import { ExerciseListPage } from '../features/exercises/ListPage'
import { ExercisePickerPage } from '../features/exercises/PickerPage'
import { ProfilePage } from '../features/profile/ProfilePage'
import { PracticePage } from '../features/practice/PracticePage'
import { SpeakingPage } from '../features/speaking/SpeakingPage'
import { WritingPage } from '../features/writing/WritingPage'
import { QaPage } from '../features/qa/QaPage'
import { AdaptiveShell } from '../layout/AdaptiveShell'
import { AppShell } from '../layout/AppShell'
import { ErrorPage, NotFoundPage } from './ErrorPages'
import { HomePage } from './HomePage'
import { LoginPage } from './LoginPage'
import { ForgotPasswordPage, ResetPasswordPage, VerifyEmailPage } from './RecoveryPages'
import { RegisterPage } from './RegisterPage'
import { PlaceholderPage } from './PlaceholderPage'

const todo = (title: string) => <PlaceholderPage title={title} />

export const routes: RouteObject[] = [
  {
    errorElement: <ErrorPage />,
    children: [
      {
        element: <PublicOnly />,
        children: [
          { path: '/dang-nhap', element: <LoginPage /> },
          { path: '/dang-ky', element: <RegisterPage /> },
        ],
      },
      { path: '/quen-mat-khau', element: <ForgotPasswordPage /> },
      // Đường dẫn tiếng Anh vì link trong email do backend tạo (MailTemplate).
      { path: '/reset-password', element: <ResetPasswordPage /> },
      { path: '/verify-email', element: <VerifyEmailPage /> },
      {
        // Công khai: khách làm Q&A và xem khóa (US-201).
        element: <AdaptiveShell />,
        children: [
          { path: '/khoa-hoc', element: <CoursesPage /> },
          { path: '/goi-y-khoa', element: <QaPage /> },
          // Trang chi tiết khóa chưa có thiết kế.
          { path: '/khoa-hoc/:slug', element: todo('Chi tiết khóa học') },
          // Công khai để khách xem bài học thử (server quyết định 401/403).
          { path: '/bai-giang/:lessonId', element: <LessonPage /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            children: [
              { path: '/', element: <HomePage /> },
              { path: '/bai-giang', element: <PickerPage /> },
              { path: '/bai-giang/khoa/:courseId', element: <CoursePage /> },
              { path: '/bai-tap', element: <ExercisePickerPage /> },
              { path: '/bai-tap/khoa/:courseId', element: <ExerciseListPage /> },
              { path: '/ho-so', element: <ProfilePage /> },
              { path: '/chuoi-hoc', element: <StreakPage /> },
              { path: '/muc-tieu', element: <GoalPage /> },
              { path: '/thanh-toan', element: <ConfirmPage /> },
              { path: '/don-hang', element: <OrdersPage /> },
              { path: '/don-hang/:code', element: <OrderPage /> },
              // Màn đích của trang chủ, chưa dựng.
              { path: '/thong-bao', element: todo('Thông báo') },
              { path: '/tro-ly-ai', element: todo('Trợ lý AI') },
              { path: '/kiem-tra-dau-vao', element: todo('Kiểm tra đầu vào') },
            ],
          },
        ],
      },
      // Màn làm bài tập trung, có header riêng nên nằm ngoài AppShell.
      {
        element: <RequireAuth />,
        children: [
          { path: '/bai-tap/:bankId', element: <PracticePage /> },
          { path: '/viet/:questionId', element: <WritingPage /> },
          { path: '/noi/:questionId', element: <SpeakingPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
