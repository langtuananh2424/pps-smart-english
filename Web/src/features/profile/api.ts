import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { SELF_LEVELS } from '../qa/content'
import type { QaAnswers } from '../qa/schema'

export type SelfLevel = QaAnswers['selfLevel']

// GET /api/me; selfLevel null khi chưa chọn.
export interface Me {
  id: number
  email: string
  emailVerified: boolean
  role: string
  displayName: string
  selfLevel: SelfLevel | null
}

export const useMe = () => useQuery({ queryKey: ['me'], queryFn: () => api<Me>('/me') })

export const levelLabel = (l: SelfLevel | null | undefined) => SELF_LEVELS.find((x) => x.value === l)?.label

export interface ProfilePatch {
  displayName: string
  selfLevel?: SelfLevel
}

export function useUpdateMe() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: ProfilePatch) => api<Me>('/me', { method: 'PATCH', body: JSON.stringify(body) }),
    // ['me'] gồm cả ['me','qa']: selfLevel trong Q&A đã được backend đồng bộ.
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me'] }),
  })
}
