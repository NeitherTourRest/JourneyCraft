import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosError,
  type AxiosResponse,
} from 'axios'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse } from '@/types/api'

// ──── Constants ────
const LOGIN_PATH = '/login'
const REFRESH_URL = '/api/auth/refresh'

// ──── Axios instance ────
const instance: AxiosInstance = axios.create({
  baseURL: '',
  timeout: 15000,
})

// ──── Refresh dedup state ────
interface SubscriberDelegate {
  resolve: (token: string) => void
  reject: (error: unknown) => void
}

let isRefreshing = false
let refreshSubscribers: SubscriberDelegate[] = []

function onRefreshed(token: string): void {
  for (const sub of refreshSubscribers) {
    sub.resolve(token)
  }
  refreshSubscribers = []
}

function onRefreshFailed(error: unknown): void {
  for (const sub of refreshSubscribers) {
    sub.reject(error)
  }
  refreshSubscribers = []
}

// ──── Request interceptor — injects token ────
instance.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

// ──── Response interceptor — handles 401, network errors ────
instance.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as
      | (AxiosRequestConfig & { _retry?: boolean })
      | undefined

    if (!originalRequest) {
      return Promise.reject(error)
    }

    // ── Network error (no response from server) ──
    if (!error.response) {
      return Promise.resolve({
        data: { code: 0, message: 'Network error', data: null },
      } as unknown as AxiosResponse)
    }

    // ── 401 Unauthorized handling ──
    if (error.response.status === 401) {
      // Prevent infinite retry loops
      if (originalRequest._retry) {
        return Promise.reject(error)
      }

      // Another refresh is already in flight — queue this request
      if (isRefreshing) {
        return new Promise<AxiosResponse>((resolve, reject) => {
          refreshSubscribers.push({
            resolve: (token: string) => {
              originalRequest.headers = originalRequest.headers || {}
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(instance(originalRequest))
            },
            reject,
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      const auth = useAuthStore()

      // No refresh token → clear auth and redirect immediately
      if (!auth.refreshToken) {
        isRefreshing = false
        onRefreshFailed(error)
        auth.clearAuth()
        window.location.hash = LOGIN_PATH
        return Promise.reject(error)
      }

      try {
        const refreshResponse = await axios.post<ApiResponse<{ token: string }>>(
          REFRESH_URL,
          { refreshToken: auth.refreshToken },
        )

        if (refreshResponse.data?.code === 200) {
          const newToken = refreshResponse.data.data.token
          auth.setAuth({
            token: newToken,
            refreshToken: auth.refreshToken!,
            user: auth.user!,
          })

          onRefreshed(newToken)
          isRefreshing = false

          originalRequest.headers = originalRequest.headers || {}
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return instance(originalRequest)
        }
      } catch {
        // Refresh request itself failed (network error etc.)
      }

      // Refresh failed
      isRefreshing = false
      onRefreshFailed(error)
      auth.clearAuth()
      window.location.hash = LOGIN_PATH
      return Promise.reject(error)
    }

    // ── Non-401 errors pass through ──
    return Promise.reject(error)
  },
)

// ──── Typed request methods ────
export const request = {
  get<T>(
    url: string,
    config?: AxiosRequestConfig,
  ): Promise<AxiosResponse<ApiResponse<T>>> {
    return instance.get<ApiResponse<T>>(url, config)
  },

  post<T>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig,
  ): Promise<AxiosResponse<ApiResponse<T>>> {
    return instance.post<ApiResponse<T>>(url, data, config)
  },

  put<T>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig,
  ): Promise<AxiosResponse<ApiResponse<T>>> {
    return instance.put<ApiResponse<T>>(url, data, config)
  },

  delete<T>(
    url: string,
    config?: AxiosRequestConfig,
  ): Promise<AxiosResponse<ApiResponse<T>>> {
    return instance.delete<ApiResponse<T>>(url, config)
  },
}

export default instance
