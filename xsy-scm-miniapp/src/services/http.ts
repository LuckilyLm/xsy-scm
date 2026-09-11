import Taro from '@tarojs/taro'
import { API_BASE } from '../config'
import { getToken, clearSession } from './storage'
import { MallErrorCode } from '../types/mall'

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message || '请求失败')
    this.code = code
    this.name = 'ApiError'
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: unknown
  header?: Record<string, string>
  idempotencyKey?: string
  query?: Record<string, unknown>
}

function buildQuery(query?: Record<string, unknown>): string {
  if (!query) return ''
  const params = new URLSearchParams()
  Object.entries(query).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') params.append(k, String(v))
  })
  const s = params.toString()
  return s ? `?${s}` : ''
}

/**
 * 统一请求封装：
 * - 自动携带 X-Mall-Token（来自本地存储）
 * - 解析 ApiResponse 信封，code !== 0 抛 ApiError
 * - 40171/40172 视为登录失效，清理会话并跳转登录页
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method || 'GET'
  const header: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.header || {}),
  }
  const token = getToken()
  if (token) header['X-Mall-Token'] = token
  if (options.idempotencyKey) header['Idempotency-Key'] = options.idempotencyKey

  const res = await Taro.request({
    url: `${API_BASE}${path}${buildQuery(options.query)}`,
    method,
    data: options.data,
    header,
  })

  const status = res.statusCode
  const body = res.data as { code?: number; message?: string; data?: T }

  if (status === 200 && body && typeof body.code === 'number') {
    if (body.code === 0) return body.data as T
    if (body.code === MallErrorCode.LOGIN_REQUIRED || body.code === MallErrorCode.TOKEN_INVALID) {
      clearSession()
      Taro.showToast({ title: '登录已失效，请重新登录', icon: 'none' })
      setTimeout(() => Taro.reLaunch({ url: '/pages/login/index' }), 800)
    }
    throw new ApiError(body.code, body.message || '请求失败')
  }

  throw new ApiError(status, `网络错误 (${status})`)
}

export const http = {
  get: <T>(path: string, query?: Record<string, unknown>) =>
    request<T>(path, { method: 'GET', query }),
  post: <T>(path: string, data?: unknown, opts?: RequestOptions) =>
    request<T>(path, { method: 'POST', data, ...opts }),
  put: <T>(path: string, data?: unknown) => request<T>(path, { method: 'PUT', data }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
