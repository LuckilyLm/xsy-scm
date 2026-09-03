import axios, { AxiosError } from 'axios';

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
});

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
    if (isEnvelope(error.response?.data)) {
      const envelope = error.response.data;
      return Promise.reject(
        new ApiError(envelope.code, envelope.message || '请求失败', error.response?.status),
      );
    }
    return Promise.reject(new ApiError(50000, '网络连接失败，请稍后重试', error.response?.status));
  },
);
