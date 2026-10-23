import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmId, ScmResponse} from '/@/types/business/scm/customer';

export type HomeBlockType = 'BANNER' | 'SHORTCUT' | 'RECOMMENDATION' | 'ACTIVITY_TOPIC' | 'ANNOUNCEMENT';
export type HomeLinkType = 'NONE' | 'PRODUCT' | 'CATEGORY' | 'ACTIVITY' | 'COUPONS' | 'SEARCH' | 'EXTERNAL_URL';

export interface HomeBlock {
  key: string;
  schemaVersion: 1;
  type: HomeBlockType;
  sortOrder: number;
  enabled: boolean;
  title?: string | null;
  subtitle?: string | null;
  text?: string | null;
  imageFileKey?: string | null;
  linkType?: HomeLinkType | null;
  linkTarget?: string | null;
  skuIds?: ScmId[] | null;
  customerIds?: ScmId[] | null;
  startAt?: string | null;
  endAt?: string | null;
}

export interface HomeDraft {
  themeCode?: string;
  version: number;
  pointerVersion: number;
  currentReleaseId: ScmId | null;
  blocks: HomeBlock[];
  updatedAt: string;
  updatedBy: string | null;
}

export interface HomeRelease {
  id: ScmId;
  publishKind: 'PUBLISH' | 'ROLLBACK';
  sourceReleaseId: ScmId | null;
  reason: string | null;
  publishedAt: string;
  publishedBy: string;
}

export interface HomeProduct {
  skuId: ScmId;
  productName: string;
  specName: string;
  primaryImageUrl?: string | null;
  unitPrice?: string | null;
  stockStatus?: string | null;
}

export interface HomeVisibleBlock {
  key: string;
  type: HomeBlockType;
  schemaVersion: 1;
  title?: string | null;
  subtitle?: string | null;
  text?: string | null;
  imageUrl?: string | null;
  linkType?: HomeLinkType | null;
  linkTarget?: string | null;
  products?: HomeProduct[] | null;
}

export interface HomePage {
  releaseId: ScmId | null;
  /** 发布版本上的主题编码；未发布时为空。 */
  themeCode?: string | null;
  blocks: HomeVisibleBlock[];
}

export const mallHomeApi = {
  draft: () => getRequest('/scm/mall-admin/home/draft', {}) as unknown as Promise<ScmResponse<HomeDraft>>,
  history: () => getRequest('/scm/mall-admin/home/history', {}) as unknown as Promise<ScmResponse<HomeRelease[]>>,
  save: (version: number, themeCode: string, blocks: HomeBlock[]) =>
    postRequest('/scm/mall-admin/home/draft', {version, themeCode, blocks}) as unknown as Promise<ScmResponse<HomeDraft>>,
  publish: (draftVersion: number, pointerVersion: number) =>
    postRequest('/scm/mall-admin/home/publish', {draftVersion, pointerVersion}) as unknown as Promise<ScmResponse<ScmId>>,
  rollback: (targetReleaseId: ScmId, pointerVersion: number, reason: string) =>
    postRequest('/scm/mall-admin/home/rollback', {targetReleaseId, pointerVersion, reason}) as unknown as Promise<ScmResponse<ScmId>>,
  preview: (customerId: ScmId, warehouseId: ScmId) =>
    postRequest('/scm/mall-admin/home/preview', {customerId, warehouseId}) as unknown as Promise<ScmResponse<HomePage>>,
};
