import type { RouteObject } from 'react-router'
import { PublicOnly, RequireAuth } from '../auth/guards'
import { AppShell } from '../layout/AppShell'
import { ErrorPage, NotFoundPage } from './ErrorPages'
import { HomePage } from './HomePage'
import { LoginPage } from './LoginPage'
import { PlaceholderPage } from './PlaceholderPage'
import { ProfilePage } from './ProfilePage'

const todo = (title: string) => <PlaceholderPage title={title} />

export const routes: RouteObject[] = [
  {
    errorElement: <ErrorPage />,
    children: [
      { element: <PublicOnly />, children: [{ path: '/dang-nhap', element: <LoginPage /> }] },
      { path: '/quen-mat-khau', element: todo('Quên mật khẩu') },
      { path: '/dang-ky', element: todo('Đăng ký') },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            children: [
              { path: '/', element: <HomePage /> },
              { path: '/khoa-hoc', element: todo('Khóa học') },
              { path: '/bai-giang', element: todo('Bài giảng') },
              { path: '/bai-tap', element: todo('Bài tập') },
              { path: '/ho-so', element: <ProfilePage /> },
            ],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
