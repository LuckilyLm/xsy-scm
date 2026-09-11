import { http } from './http'
import type { MallLoginResult, MallProfile } from '../types/mall'

export function login(username: string, password: string) {
  return http.post<MallLoginResult>('/api/mall/auth/login', { username, password })
}

export function logout() {
  return http.post<void>('/api/mall/auth/logout')
}

export function getProfile() {
  return http.get<MallProfile>('/api/mall/auth/profile')
}

/** 微信登录占位：当前后端返回 WECHAT_LOGIN_UNAVAILABLE，前端先固定契约。 */
export function wechatLogin(code: string) {
  return http.post<MallLoginResult>('/api/mall/auth/wechat-login', { code })
}
