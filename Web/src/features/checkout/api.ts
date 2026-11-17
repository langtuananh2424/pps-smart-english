import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'

export type OrderStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELLED'

export interface PaymentInfo {
  qrContent: string
  expiresAt: string
  simulatable: boolean
}

// POST /api/orders (201)
export interface OrderCreated {
  code: string
  status: OrderStatus
  totalVnd: number
  payment: PaymentInfo
}

// GET /api/orders/{code}; payment và transferContent chỉ có khi PENDING, status là trạng thái hiệu lực.
export interface OrderView {
  code: string
  status: OrderStatus
  totalVnd: number
  paidAt?: string
  expiresAt: string
  items: { productId: number; name: string; priceVnd: number; course: { id: number; slug: string; title: string } | null }[]
  payment?: PaymentInfo
  transferContent?: string
}

// GET /api/me/orders
export interface OrderSummary {
  code: string
  status: OrderStatus
  totalVnd: number
  createdAt: string
  paidAt: string | null
  expiresAt: string
  items: { productId: number; name: string }[]
}

export const orderKey = (code: string) => ['order', code] as const
const POLL_MS = 3000

export function useCreateOrder() {
  return useMutation({
    mutationFn: ({ productIds, key }: { productIds: number[]; key: string }) =>
      api<OrderCreated>('/orders', { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify({ productIds }) }),
  })
}

// Đơn chờ thanh toán: hỏi lại mỗi 3 giây (TanStack tự dừng khi tab ẩn).
export const useOrder = (code: string) =>
  useQuery({
    queryKey: orderKey(code),
    queryFn: () => api<OrderView>(`/orders/${code}`),
    refetchInterval: (q) => (q.state.data?.status === 'PENDING' ? POLL_MS : false),
  })

export function useSimulatePay(code: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api<OrderView>(`/dev/payments/${code}/pay`, { method: 'POST' }),
    onSuccess: (o) => qc.setQueryData(orderKey(code), o),
  })
}

export const useMyOrders = () =>
  useInfiniteQuery({
    queryKey: ['me', 'orders'],
    queryFn: ({ pageParam }) =>
      api<{ data: OrderSummary[]; nextCursor: string | null }>(`/me/orders?limit=20${pageParam ? `&cursor=${pageParam}` : ''}`),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })

export const isApiError = (e: unknown, status: number, code?: string): e is ApiError =>
  e instanceof ApiError && e.status === status && (code == null || e.code === code)
