// @vitest-environment node
import { describe, it, expect, beforeEach, vi } from 'vitest'

// ──── Polyfill window for Node test environment ────
if (typeof window === 'undefined') {
  ;(globalThis as unknown as Record<string, unknown>).window = {
    location: {
      hash: '',
      assign: vi.fn(),
      replace: vi.fn(),
      reload: vi.fn(),
      get href() {
        return ''
      },
      set href(_v: string) {
        /* noop */
      },
    },
  }
}

// ──── Shared mock state ────
const {
  mockGet,
  mockPost,
  mockPut,
  mockDelete,
  mockAxiosPost,
  setRequestInterceptor,
  setResponseErrorInterceptor,
  getRequestInterceptor,
  getResponseErrorInterceptor,
} = vi.hoisted(() => {
  let reqInterceptor: ((config: any) => any) | null = null
  let resErrorInterceptor: ((error: any) => any) | null = null

  return {
    mockGet: vi.fn(),
    mockPost: vi.fn(),
    mockPut: vi.fn(),
    mockDelete: vi.fn(),
    mockAxiosPost: vi.fn(),
    setRequestInterceptor: (fn: any) => {
      reqInterceptor = fn
    },
    setResponseErrorInterceptor: (fn: any) => {
      resErrorInterceptor = fn
    },
    getRequestInterceptor: () => reqInterceptor,
    getResponseErrorInterceptor: () => resErrorInterceptor,
  }
})

// ──── Mock useAuthStore ────
// setAuth / clearAuth actually mutate state (not just spy), so retries pick up the new token.
const mockAuthStore = {
  token: null as string | null,
  refreshToken: null as string | null,
  user: null as any,
  setAuth: vi.fn((data: { token: string; refreshToken: string; user: any }) => {
    mockAuthStore.token = data.token
    mockAuthStore.refreshToken = data.refreshToken
    mockAuthStore.user = data.user
    mockAuthStore.isAuthenticated = true
  }),
  clearAuth: vi.fn(() => {
    mockAuthStore.token = null
    mockAuthStore.refreshToken = null
    mockAuthStore.user = null
    mockAuthStore.isAuthenticated = false
  }),
  isAuthenticated: false,
}

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mockAuthStore,
}))

// ──── Mock axios ────
vi.mock('axios', () => {
  // Helper: runs stored request interceptor on a config
  function applyRequestInterceptor(config: any): any {
    const interceptor = getRequestInterceptor()
    return interceptor ? interceptor(config) : config
  }

  // Emulate axios's automatic Content-Type detection
  function setDefaultContentType(merged: Record<string, any>, data: unknown): void {
    if (!merged.headers) merged.headers = {}
    if (!merged.headers['Content-Type']) {
      if (data instanceof URLSearchParams) {
        merged.headers['Content-Type'] = 'application/x-www-form-urlencoded'
      } else if (data != null && typeof data === 'object') {
        merged.headers['Content-Type'] = 'application/json'
      }
    }
  }

  // HTTP methods — run interceptor, set content-type, then delegate
  const methods = {
    get: vi.fn((url: string, config?: any) => {
      const merged = config ? { ...config, url } : { url }
      const final = applyRequestInterceptor(merged)
      return mockGet(url, final)
    }),
    post: vi.fn((url: string, data?: any, config?: any) => {
      const merged = config ? { ...config, url, data } : { url, data }
      setDefaultContentType(merged, data)
      const final = applyRequestInterceptor(merged)
      return mockPost(url, data, final)
    }),
    put: vi.fn((url: string, data?: any, config?: any) => {
      const merged = config ? { ...config, url, data } : { url, data }
      setDefaultContentType(merged, data)
      const final = applyRequestInterceptor(merged)
      return mockPut(url, data, final)
    }),
    delete: vi.fn((url: string, config?: any) => {
      const merged = config ? { ...config, url } : { url }
      const final = applyRequestInterceptor(merged)
      return mockDelete(url, final)
    }),
  }

  // Callable wrapper — used by interceptor retry: instance(originalRequest)
  const callableRequest = vi.fn((config: any) => {
    const final = applyRequestInterceptor(config)
    return mockGet(config.url, final)
  })

  const mockInstance = Object.assign(callableRequest, {
    interceptors: {
      request: {
        use: vi.fn((fn: any) => {
          setRequestInterceptor(fn)
        }),
      },
      response: {
        use: vi.fn((_success: any, error: any) => {
          setResponseErrorInterceptor(error)
        }),
      },
    },
    ...methods,
  })

  return {
    default: {
      create: vi.fn(() => mockInstance),
      post: mockAxiosPost,
    },
  }
})

// ──── Import module under test AFTER all mocks ────
import { request } from '../request'

describe('request.ts — Axios wrapper', () => {
  beforeEach(() => {
    vi.clearAllMocks()

    mockAuthStore.token = null
    mockAuthStore.refreshToken = null
    mockAuthStore.user = null
    mockAuthStore.isAuthenticated = false

    // Reset window.location.hash
    if (typeof window !== 'undefined') {
      ;(window as any).location.hash = ''
    }
  })

  // ────────────────────────────────────────────────
  // Test 1: Token injection
  // ────────────────────────────────────────────────
  it('injects Authorization header when auth store has a token', async () => {
    mockAuthStore.token = 'test-jwt-token'
    mockGet.mockResolvedValue({ data: { code: 200, message: 'ok', data: null } })

    await request.get('/api/protected')

    expect(mockGet).toHaveBeenCalledTimes(1)
    const callArg = mockGet.mock.calls[0]?.[1] as any
    expect(callArg?.headers?.Authorization).toBe('Bearer test-jwt-token')
  })

  // ────────────────────────────────────────────────
  // Test 2: No Authorization for public endpoints
  // ────────────────────────────────────────────────
  it('does not inject Authorization header when token is null', async () => {
    mockAuthStore.token = null
    mockPost.mockResolvedValue({ data: { code: 200, message: 'ok', data: null } })

    await request.post('/api/auth/login', { username: 'foo', password: 'bar' })

    expect(mockPost).toHaveBeenCalledTimes(1)
    const callArg = mockPost.mock.calls[0]?.[2] as any
    expect(callArg?.headers?.Authorization).toBeUndefined()
  })

  // ────────────────────────────────────────────────
  // Test 3: JSON Content-Type
  // ────────────────────────────────────────────────
  it('sets Content-Type to application/json for JSON data', async () => {
    mockAuthStore.token = 'tok'
    mockPost.mockResolvedValue({ data: { code: 200, message: 'ok', data: null } })

    await request.post('/api/data', { foo: 'bar' })

    const callArg = mockPost.mock.calls[0]?.[2] as any
    expect(callArg?.headers?.['Content-Type']).toBe('application/json')
  })

  // ────────────────────────────────────────────────
  // Test 4: Form-urlencoded Content-Type
  // ────────────────────────────────────────────────
  it('sets Content-Type to application/x-www-form-urlencoded for URLSearchParams', async () => {
    mockAuthStore.token = 'tok'
    mockPost.mockResolvedValue({ data: { code: 200, message: 'ok', data: null } })

    const params = new URLSearchParams({ key: 'value' })
    await request.post('/api/oauth/token', params)

    const callArg = mockPost.mock.calls[0]?.[2] as any
    expect(callArg?.headers?.['Content-Type']).toBe('application/x-www-form-urlencoded')
  })

  // ────────────────────────────────────────────────
  // Test 5: 401 → refresh → retry success
  // ────────────────────────────────────────────────
  it('on 401, refreshes token and retries the original request', async () => {
    mockAuthStore.token = 'expired-token'
    mockAuthStore.refreshToken = 'refresh-token-abc'

    const err401 = {
      config: { url: '/api/protected', headers: {} },
      response: { status: 401, data: { code: 401, message: 'Unauthorized' } },
    }

    // Refresh call succeeds
    mockAxiosPost.mockResolvedValue({
      data: { code: 200, message: 'ok', data: { token: 'new-jwt-token' } },
    })

    // Retry succeeds
    mockGet.mockResolvedValue({ data: { code: 200, message: 'ok', data: { name: 'data' } } })

    const errorHandler = getResponseErrorInterceptor()
    if (!errorHandler) throw new Error('Response error interceptor not registered')

    const result = await errorHandler(err401)

    expect(result?.data?.code).toBe(200)
    expect(result?.data?.data).toEqual({ name: 'data' })

    // Verify refresh was called
    expect(mockAxiosPost).toHaveBeenCalledWith('/api/auth/refresh', {
      refreshToken: 'refresh-token-abc',
    })

    // Verify auth store was updated
    expect(mockAuthStore.setAuth).toHaveBeenCalledWith(
      expect.objectContaining({ token: 'new-jwt-token' }),
    )

    // Verify retry had new token
    const retryCall = mockGet.mock.calls[0]?.[1] as any
    expect(retryCall?.headers?.Authorization).toBe('Bearer new-jwt-token')
  })

  // ────────────────────────────────────────────────
  // Test 6: 401 → refresh fails → clearAuth + redirect
  // ────────────────────────────────────────────────
  it('clears auth and redirects to login when refresh also fails', async () => {
    mockAuthStore.token = 'expired-token'
    mockAuthStore.refreshToken = 'refresh-token-bad'

    const err401 = {
      config: { url: '/api/protected', headers: {} },
      response: { status: 401, data: { code: 401, message: 'Unauthorized' } },
    }

    // Refresh also returns non-200
    mockAxiosPost.mockResolvedValue({
      data: { code: 401, message: 'Refresh token expired' },
    })

    // Spy on window.location.hash setter
    const hashSpy = vi.fn()
    Object.defineProperty(window.location, 'hash', {
      get: () => '',
      set: hashSpy,
      configurable: true,
    })

    const errorHandler = getResponseErrorInterceptor()
    if (!errorHandler) throw new Error('Response error interceptor not registered')

    await expect(errorHandler(err401)).rejects.toBeDefined()

    // clearAuth should have been called
    expect(mockAuthStore.clearAuth).toHaveBeenCalled()

    // Redirect to login
    expect(hashSpy).toHaveBeenCalledWith('/login')
  })

  // ────────────────────────────────────────────────
  // Test 7: Network error → structured response
  // ────────────────────────────────────────────────
  it('returns structured error for network failures (no response)', async () => {
    const networkError = {
      config: { url: '/api/data', headers: {} },
      message: 'Network Error',
      code: 'ERR_NETWORK',
      // purposefully: no "response" property
    }

    const errorHandler = getResponseErrorInterceptor()
    if (!errorHandler) throw new Error('Response error interceptor not registered')

    const result = await errorHandler(networkError)
    expect(result?.data?.code).toBe(0)
    expect(result?.data?.message).toBe('Network error')
    expect(result?.data?.data).toBeNull()
    expect(mockAuthStore.clearAuth).not.toHaveBeenCalled()
  })

  // ────────────────────────────────────────────────
  // Test 8: Non-401 errors pass through
  // ────────────────────────────────────────────────
  it('passes through non-401 errors unchanged', async () => {
    const serverError = {
      config: { url: '/api/data', headers: {} },
      response: { status: 500, data: { code: 500, message: 'Server Error' } },
    }

    const errorHandler = getResponseErrorInterceptor()
    if (!errorHandler) throw new Error('Response error interceptor not registered')

    await expect(errorHandler(serverError)).rejects.toBeDefined()
    expect(mockAuthStore.clearAuth).not.toHaveBeenCalled()
  })

  // ────────────────────────────────────────────────
  // Test 9: Refresh dedup — multiple concurrent 401s
  // ────────────────────────────────────────────────
  it('queues concurrent 401 requests and resolves all after single refresh', async () => {
    mockAuthStore.token = 'expired-token'
    mockAuthStore.refreshToken = 'refresh-token-concurrent'

    const err401a = {
      config: { url: '/api/a', headers: {} },
      response: { status: 401, data: { code: 401 } },
    }
    const err401b = {
      config: { url: '/api/b', headers: {} },
      response: { status: 401, data: { code: 401 } },
    }

    // Refresh succeeds
    mockAxiosPost.mockResolvedValue({
      data: { code: 200, message: 'ok', data: { token: 'fresh-token' } },
    })

    // Retries succeed
    mockGet.mockResolvedValue({
      data: { code: 200, message: 'ok', data: { resolved: true } },
    })

    const errorHandler = getResponseErrorInterceptor()
    if (!errorHandler) throw new Error('Response error interceptor not registered')

    // Fire both 401s concurrently
    const [resA, resB] = await Promise.all([
      errorHandler(err401a),
      errorHandler(err401b),
    ])

    expect(resA?.data?.code).toBe(200)
    expect(resB?.data?.code).toBe(200)

    // Only ONE refresh call should have been made
    expect(mockAxiosPost).toHaveBeenCalledTimes(1)
  })

  // ────────────────────────────────────────────────
  // Test 10: GET / POST / PUT / DELETE convenience methods
  // ────────────────────────────────────────────────
  it('exposes typed get / post / put / delete methods', async () => {
    mockAuthStore.token = 'tok'
    mockGet.mockResolvedValue({ data: { code: 200, message: 'ok', data: [] } })
    mockPost.mockResolvedValue({ data: { code: 200, message: 'ok', data: {} } })
    mockPut.mockResolvedValue({ data: { code: 200, message: 'ok', data: {} } })
    mockDelete.mockResolvedValue({ data: { code: 200, message: 'ok', data: null } })

    await request.get('/api/items')
    await request.post('/api/items', { name: 'x' })
    await request.put('/api/items/1', { name: 'y' })
    await request.delete('/api/items/1')

    expect(mockGet).toHaveBeenCalledTimes(1)
    expect(mockPost).toHaveBeenCalledTimes(1)
    expect(mockPut).toHaveBeenCalledTimes(1)
    expect(mockDelete).toHaveBeenCalledTimes(1)
  })
})
