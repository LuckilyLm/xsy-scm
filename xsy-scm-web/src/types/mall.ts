export type MallCardStyle = 'FLAT' | 'SHADOW' | 'BORDER';
export type MallProductCardStyle = 'COMPACT' | 'COMFORTABLE' | 'SPACIOUS';
export type MallNavigationStyle = 'BOTTOM' | 'TOP' | 'SIDEBAR';

export interface MallThemeConfig {
  themeCode: string;
  primaryColor: string;
  accentColor: string;
  pageBackground: string;
  cardRadius: number;
  cardStyle: MallCardStyle;
  productCardStyle: MallProductCardStyle;
  navigationStyle: MallNavigationStyle;
}

export interface MallHomeSection {
  id: number;
  sectionType: string;
  title?: string;
  sortOrder: number;
  payload?: Record<string, unknown>;
  promotionId?: number;
  categoryId?: number;
}

export interface MallCategory {
  id: number;
  parentId: number | null;
  name: string;
  level: number;
  sortOrder: number;
  productCount: number;
}

export interface MallHomeProduct {
  skuId: number;
  spuId?: number;
  productName: string;
  skuCode?: string;
  categoryName?: string;
  specName?: string;
  saleUnit?: string;
  imageUrl?: string | null;
  unitPrice: string | null;
  marketPrice?: string | null;
  priceSource?: string | null;
}

export interface MallPromotion {
  id: number;
  name: string;
  type: string;
  scopeType: string;
  status: string;
  startAt?: string;
  endAt?: string;
  description?: string;
  effective?: boolean;
}

export interface MallHomeResponse {
  theme: MallThemeConfig;
  sections: MallHomeSection[];
  categories: MallCategory[];
  promotions: MallPromotion[];
  generatedAt: string;
}
