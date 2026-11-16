import { QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { AuthProvider } from './auth/AuthContext'
import { makeQueryClient } from './lib/queryClient'
import { routes } from './routes'

const queryClient = makeQueryClient()
const router = createBrowserRouter(routes)

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  )
}
