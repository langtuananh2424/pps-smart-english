import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { qaSchema, type QaAnswers } from './schema'

// GET /me/qa trả 204 khi chưa làm → null; dữ liệu sai dạng cũng coi như chưa có.
export const useMyQa = (enabled = true) =>
  useQuery({
    queryKey: ['me', 'qa'],
    enabled,
    queryFn: async () => {
      const r = await api<unknown>('/me/qa')
      const p = qaSchema.safeParse(r)
      return p.success ? p.data : null
    },
  })

export function useSaveQa() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (a: QaAnswers) => api('/me/qa', { method: 'PUT', body: JSON.stringify(qaSchema.parse(a)) }),
    onSuccess: () => Promise.all([qc.invalidateQueries({ queryKey: ['me', 'qa'] }), qc.invalidateQueries({ queryKey: ['me', 'suggestions'] })]),
  })
}
