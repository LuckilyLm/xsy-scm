import axios from 'axios';
import type { AxiosError } from 'axios';

interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

function isEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    typeof (value as { code?: unknown }).code === 'number'
  );
}

export class ApiError extends Error {
  constructor(
    public readonly code: number,
    message: string,
    public readonly status?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export const apiClient = axios.create({
  baseURL: '/api',
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
  // 会话由后端 HttpOnly Cookie `XSY_SESSION` 持有，不写入 Web Storage。
  withCredentials: true,
  // 与后端 CookieCsrfTokenRepository 的 cookie/header 名称保持一致。
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withXSRFToken: true,
});

/**
 * 登录接口自身的 401 表示凭据错误，不能当作“会话失效”触发全局登出，
 * 否则输入错误密码时会被立刻判定为已登出。
 */
function isCredentialProbe(url: string | undefined): boolean {
  return url === '/auth/login' || url === '/auth/csrf';
}

type UnauthorizedListener = () => void;

let unauthorizedListener: UnauthorizedListener | null = null;
let unauthorizedSignaled = false;

/** 由 AuthProvider 注册，用于在任意请求返回 401 时清理内存中的认证状态。 */
export function setUnauthorizedListener(listener: UnauthorizedListener | null): void {
  unauthorizedListener = listener;
}

/** 登录成功后复位，避免上一次会话的 401 抑制后续通知。 */
export function resetUnauthorizedSignal(): void {
  unauthorizedSignaled = false;
}

/** 并发的多个 401 只通知一次，避免重复跳转或重复提示。 */
function signalUnauthorized(): void {
  if (unauthorizedSignaled) {
    return;
  }
  unauthorizedSignaled = true;
  unauthorizedListener?.();
}

apiClient.interceptors.response.use(
  (response) => {
    if (!isEnvelope(response.data)) {
      throw new ApiError(50000, '服务响应格式不正确', response.status);
    }
    if (response.data.code !== 0) {
      throw new ApiError(response.data.code, response.data.message || '请求失败', response.status);
    }
    response.data = response.data.data;
    return response;
  },
  (error: AxiosError) => {
    if (error.response?.status === 401 && !isCredentialProbe(error.config?.url)) {
      signalUnauthorized();
    }
    if (isEnvelope(error.response?.data)) {
      const envelope = error.response.data;
      return Promise.reject(
        new ApiError(envelope.code, envelope.message || '请求失败', error.response?.status),
      );
    }
    return Promise.reject(new ApiError(50000, '网络连接失败，请稍后重试', error.response?.status));
  },
);
