import { apiClient } from './http';
import type { MallThemeConfig } from '../types/mall';
import type {
  HomeSection,
  HomeSectionSaveRequest,
  Promotion,
  PromotionPage,
  PromotionPageParams,
} from '../types/marketing';

// 后台商城配置走会话认证的 /marketing/**。
// 商城端 /mall/** 使用客户令牌（X-Mall-Token），后台会话访问必然 401，故此处不得复用。

export async function fetchThemeConfig(): Promise<MallThemeConfig> {
  return (await apiClient.get<MallThemeConfig>('/marketing/theme')).data;
}

export async function saveThemeConfig(payload: MallThemeConfig): Promise<MallThemeConfig> {
  return (await apiClient.put<MallThemeConfig>('/marketing/theme', payload)).data;
}

export async function fetchHomeSections(): Promise<HomeSection[]> {
  return (await apiClient.get<HomeSection[]>('/marketing/home-sections')).data;
}

export async function createHomeSection(payload: HomeSectionSaveRequest): Promise<HomeSection> {
  return (await apiClient.post<HomeSection>('/marketing/home-sections', payload)).data;
}

export async function updateHomeSection(
  id: number,
  payload: HomeSectionSaveRequest,
): Promise<HomeSection> {
  return (await apiClient.put<HomeSection>(`/marketing/home-sections/${id}`, payload)).data;
}

export async function fetchPromotions(params: PromotionPageParams): Promise<PromotionPage> {
  return (await apiClient.get<PromotionPage>('/marketing/promotions', { params })).data;
}

export async function fetchEffectivePromotions(): Promise<Promotion[]> {
  return (await apiClient.get<Promotion[]>('/marketing/promotions/effective')).data;
}
