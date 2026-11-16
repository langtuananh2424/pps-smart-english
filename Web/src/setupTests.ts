import '@testing-library/jest-dom/vitest'

// jsdom và undici dùng hai lớp AbortSignal khác nhau; data router tạo Request kèm signal sẽ lỗi.
const NodeRequest = globalThis.Request
globalThis.Request = class extends NodeRequest {
  constructor(input: RequestInfo | URL, init?: RequestInit) {
    super(input, init && { ...init, signal: undefined })
  }
} as typeof Request
