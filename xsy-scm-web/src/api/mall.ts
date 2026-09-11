import { apiClient } from './http';
import type { MallHomeResponse, MallThemeConfig } from '../types/mall';

// 商城端接口，走商城客户令牌链（X-Mall-Token），由小程序 / 商城 H5 使用。
export async function fetchMallHome(): Promise<MallHomeResponse> {
  const response = await apiClient.get<MallHomeResponse>('/mall/home');
  return response.data;
}

export async function fetchMallTheme(): Promise<MallThemeConfig> {
  const response = await apiClient.get<MallThemeConfig>('/mall/theme');
  return response.data;
}

/**
 * 后台商城预览。后台会话持有的是 Session + CSRF，无法通过商城客户令牌链，
 * 因此后台预览改走 /marketing/home-preview，返回结构与 /mall/home 完全一致。
 */
export async function fetchMallHomePreview(): Promise<MallHomeResponse> {
  const response = await apiClient.get<MallHomeResponse>('/marketing/home-preview');
  return response.data;
}
