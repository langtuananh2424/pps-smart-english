import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './api'

// Lỗi 4xx là lỗi của request, thử lại không đổi kết quả.
export const makeQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: (count, err) => !(err instanceof ApiError && err.status >= 400 && err.status < 500) && count < 2,
      },
    },
  })
