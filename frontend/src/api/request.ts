import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosError,
  type AxiosResponse,
} from 'axios'
import type { ApiResponse } from '@/types/api'

// ──── Axios instance ────
const instance: AxiosInstance = axios.create({
  baseURL: '',
  timeout: 15000,
})

// ──── Response interceptor — handles network errors only ────
instance.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as AxiosRequestConfig | undefined
    if (!originalRequest) return Promise.reject(error)

    // Network error (no response from server)
    if (!error.response) {
      return Promise.resolve({
        data: { code: 0, message: 'Network error', data: null },
      } as unknown as AxiosResponse)
    }

    // Non-401 errors pass through
    return Promise.reject(error)
  },
)

// ──── Typed request methods ────
export const request = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<AxiosResponse<ApiResponse<T>>> {
    return instance.get<ApiResponse<T>>(url, config)
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<AxiosResponse<ApiResponse<T>>> {
    return instance.post<ApiResponse<T>>(url, data, config)
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<AxiosResponse<ApiResponse<T>>> {
    return instance.put<ApiResponse<T>>(url, data, config)
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<AxiosResponse<ApiResponse<T>>> {
    return instance.delete<ApiResponse<T>>(url, config)
  },
}

export default instance
