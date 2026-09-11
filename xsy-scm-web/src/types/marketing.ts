// 营销领域类型定义，对应后端 com.xianshuyuan.scm.marketing.vo / dto。
// 后台侧统一走会话认证的 /api/marketing/**，与商城端 /api/mall/** 分离。

import type { PageData } from './product';

export type { PageData };

export type HomeSectionType =
  | 'BANNER'
  | 'FLASH_SALE'
  | 'NEW_ARRIVAL'
  | 'CATEGORY'
  | 'RECOMMEND'
  | 'CUSTOM';

export type HomeSectionStatus = 'ENABLED' | 'DISABLED';

export type PromotionType = 'FLASH_SALE' | 'FULL_REDUCE' | 'FULL_GIFT' | 'TIME_LIMIT';
export type PromotionScope = 'ALL' | 'CUSTOMER' | 'CATEGORY' | 'SKU';
export type PromotionStatus = 'DRAFT' | 'ENABLED' | 'DISABLED' | 'EXPIRED';

export const homeSectionTypeLabels: Record<HomeSectionType, string> = {
  BANNER: '运营横幅',
  FLASH_SALE: '限时秒杀',
  NEW_ARRIVAL: '新品上架',
  CATEGORY: '分类导航',
  RECOMMEND: '推荐商品',
  CUSTOM: '自定义板块',
};

export const homeSectionStatusLabels: Record<HomeSectionStatus, string> = {
  ENABLED: '已启用',
  DISABLED: '已停用',
};

export const promotionTypeLabels: Record<PromotionType, string> = {
  FLASH_SALE: '限时秒杀',
  FULL_REDUCE: '满减',
  FULL_GIFT: '满赠',
  TIME_LIMIT: '时段特价',
};

export const promotionScopeLabels: Record<PromotionScope, string> = {
  ALL: '全站',
  CUSTOMER: '指定客户',
  CATEGORY: '指定分类',
  SKU: '指定商品',
};

export const promotionStatusLabels: Record<PromotionStatus, string> = {
  DRAFT: '草稿',
  ENABLED: '已启用',
  DISABLED: '已停用',
  EXPIRED: '已过期',
};

/**
 * 通知 / 弹窗属于运营位而非商品位，后端用 CUSTOM 板块 + payload.kind 承载，
 * 这样无需新增表即可复用同一套排序与启停能力。
 */
export type CustomSectionKind = 'NOTICE' | 'POPUP';

export const customSectionKindLabels: Record<CustomSectionKind, string> = {
  NOTICE: '公告通知',
  POPUP: '启动弹窗',
};

export interface HomeSectionPayload {
  kind?: CustomSectionKind;
  /** 公告 / 弹窗正文。 */
  content?: string;
  /** 弹窗跳转链接。 */
  linkUrl?: string;
  /** 图片地址，BANNER 与弹窗共用。 */
  imageUrl?: string;
  /** 弹窗是否每次进入都展示。 */
  alwaysShow?: boolean;
  [key: string]: unknown;
}

export interface HomeSection {
  id: number;
  sectionType: HomeSectionType;
  title: string | null;
  promotionId: number | null;
  categoryId: number | null;
  sortOrder: number;
  status: HomeSectionStatus;
  startAt: string | null;
  endAt: string | null;
  payload: HomeSectionPayload | null;
}

export interface HomeSectionSaveRequest {
  sectionType: HomeSectionType;
  title?: string | null;
  promotionId?: number | null;
  categoryId?: number | null;
  sortOrder?: number | null;
  status?: HomeSectionStatus | null;
  startAt?: string | null;
  endAt?: string | null;
  payload?: HomeSectionPayload | null;
}

export interface Promotion {
  id: number;
  name: string;
  type: PromotionType;
  scopeType: PromotionScope;
  scopeIds: unknown;
  thresholdAmount: number | null;
  discountRate: number | null;
  reduceAmount: number | null;
  promoPrice: number | null;
  giftSkuId: number | null;
  giftQuantity: number | null;
  limitQuantity: number | null;
  startAt: string | null;
  endAt: string | null;
  status: PromotionStatus;
  priority: number | null;
  description: string | null;
  effective: boolean;
}

export interface PromotionPageParams {
  page?: number;
  pageSize?: number;
  keyword?: string;
  type?: PromotionType;
  status?: PromotionStatus;
}

export type PromotionPage = PageData<Promotion>;
