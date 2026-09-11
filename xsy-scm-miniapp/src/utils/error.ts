import Taro from '@tarojs/taro'
import { ApiError } from '../services/http'

/**
 * 统一把后端业务错误暴露给用户。
 * 请求层已把 ApiResponse.message 还原到 ApiError.message，这里只负责“不再静默失败”。
 */
export function errorMessage(error: unknown, fallback = '操作失败，请稍后重试'): string {
  if (error instanceof ApiError && error.message) return error.message
  if (error instanceof Error && error.message) return error.message
  return fallback
}

export function showApiError(error: unknown, fallback = '操作失败，请稍后重试'): string {
  const message = errorMessage(error, fallback)
  Taro.showToast({ title: message, icon: 'none' })
  return message
}
