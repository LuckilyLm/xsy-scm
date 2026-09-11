import Taro from '@tarojs/taro'
import type { MallProfile } from '../types/mall'

const TOKEN_KEY = 'mall_token'
const PROFILE_KEY = 'mall_profile'

export function getToken(): string | null {
  try {
    return Taro.getStorageSync(TOKEN_KEY) || null
  } catch {
    return null
  }
}

export function setToken(token: string): void {
  try {
    Taro.setStorageSync(TOKEN_KEY, token)
  } catch {
    /* ignore */
  }
}

export function clearToken(): void {
  try {
    Taro.removeStorageSync(TOKEN_KEY)
  } catch {
    /* ignore */
  }
}

export function getProfile(): MallProfile | null {
  try {
    return Taro.getStorageSync(PROFILE_KEY) || null
  } catch {
    return null
  }
}

export function setProfile(profile: MallProfile): void {
  try {
    Taro.setStorageSync(PROFILE_KEY, profile)
  } catch {
    /* ignore */
  }
}

export function clearProfile(): void {
  try {
    Taro.removeStorageSync(PROFILE_KEY)
  } catch {
    /* ignore */
  }
}

export function clearSession(): void {
  clearToken()
  clearProfile()
}
