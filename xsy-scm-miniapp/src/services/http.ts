import Taro from '@tarojs/taro'
import { API_BASE } from '../config'
import { getToken, clearSession } from './storage'
import { MallErrorCode } from '../types/mall'

export class ApiError extends Error {
  /** 后端业务错误码（ApiResponse.code），非业务错误时为 0 或 HTTP 状态码。 */
  code: number
  /** 原始 HTTP 状态码，便于区分“业务拒绝”和“网络/网关异常”。 */
  httpStatus: number
  constructor(code: number, message: string, httpStatus = 0) {
    super(message || '请求失败')
    this.code = code
    this.httpStatus = httpStatus
    this.name = 'ApiError'
  }
}

/** 判定 ApiError，可选地同时判定业务码。 */
export function isApiError(error: unknown, code?: number): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code)
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: unknown
  header?: Record<string, string>
  idempotencyKey?: string
  query?: Record<string, unknown>
}

interface Envelope<T> {
  code?: number
  message?: string
  data?: T
}

/** 认证接口自身的失败不应触发“登录失效跳转”，否则会与登录页互相重定向。 */
const AUTH_PATHS = ['/api/mall/auth/login', '/api/mall/auth/wechat-login']

let redirecting = false

function isAuthPath(path: string): boolean {
  return AUTH_PATHS.some((item) => path.startsWith(item))
}

function handleSessionExpired(): void {
  if (redirecting) return
  redirecting = true
  clearSession()
  Taro.showToast({ title: '登录已失效，请重新登录', icon: 'none' })
  setTimeout(() => {
    redirecting = false
    Taro.reLaunch({ url: '/pages/login/index' })
  }, 800)
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
 * - 后端业务错误以 `{ code, message, data }` 信封返回，且 HTTP 状态码与业务域一致
 *   （401/403/404/409/501）。因此**任意状态码**都优先解析信封，
 *   否则 40170 登录失败、40172 登录失效、40970 变价、40372 可见性等错误会全部退化为“网络错误”。
 * - code === 0 视为成功；40171/40172 视为登录失效，清理会话并跳转登录页。
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

  let res: { statusCode: number; data: unknown }
  try {
    res = await Taro.request({
      url: `${API_BASE}${path}${buildQuery(options.query)}`,
      method,
      data: options.data,
      header,
    })
  } catch {
    throw new ApiError(0, '网络连接失败，请检查网络后重试')
  }

  const status = res.statusCode
  const body = res.data as Envelope<T> | undefined

  // 信封优先：不论 200 还是 4xx/5xx，只要带业务码就按业务错误处理。
  if (body && typeof body.code === 'number') {
    if (body.code === 0) return body.data as T
    if (
      !isAuthPath(path) &&
      (body.code === MallErrorCode.LOGIN_REQUIRED || body.code === MallErrorCode.TOKEN_INVALID)
    ) {
      handleSessionExpired()
    }
    throw new ApiError(body.code, body.message || '请求失败', status)
  }

  throw new ApiError(status, `网络错误 (${status})`, status)
}

export const http = {
  get: <T>(path: string, query?: Record<string, unknown>) =>
    request<T>(path, { method: 'GET', query }),
  post: <T>(path: string, data?: unknown, opts?: RequestOptions) =>
    request<T>(path, { method: 'POST', data, ...opts }),
  put: <T>(path: string, data?: unknown) => request<T>(path, { method: 'PUT', data }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
