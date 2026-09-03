export type ShelfStatus = 'ON_SHELF' | 'OFF_SHELF';
export type ProductType = 'STANDARD' | 'NON_STANDARD';

export interface ProductSku {
  id: number;
  version: number;
  skuCode: string;
  barcode: string | null;
  specName: string;
  specValues: Record<string, string>;
  saleUnit: string;
  productType: ProductType;
  marketPrice: string;
  status: ShelfStatus;
  defaultSku: boolean;
  sortOrder: number;
}

export interface ProductSummary {
  id: number;
  version: number;
  spuCode: string;
  name: string;
  alias: string | null;
  categoryId: number;
  categoryPath: string;
  defaultSku: ProductSku;
  skuCount: number;
  minMarketPrice: string;
  maxMarketPrice: string;
  status: ShelfStatus;
  updatedAt: string;
  skus: ProductSku[];
}

export interface ProductDetail {
  id: number;
  version: number;
  spuCode: string;
  name: string;
  alias: string | null;
  categoryId: number;
  categoryPath: string;
  description: string | null;
  status: ShelfStatus;
  createdAt: string;
  updatedAt: string;
  skus: ProductSku[];
}

export interface ProductCategoryTreeNode {
  id: number;
  parentId: number | null;
  name: string;
  level: number;
  enabled: boolean;
  selectable: boolean;
  children: ProductCategoryTreeNode[];
}

export interface PageData<T> {
  records: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface ProductPageParams {
  page: number;
  pageSize: number;
  keyword?: string;
  categoryId?: number;
  spuStatus?: ShelfStatus;
  skuStatus?: ShelfStatus;
  productType?: ProductType;
}

export interface ProductSkuPayload {
  id: number | null;
  version: number | null;
  skuCode: string;
  barcode: string | null;
  specName: string;
  specValues: Record<string, string>;
  saleUnit: string;
  productType: ProductType;
  marketPrice: string;
  status: ShelfStatus;
  defaultSku: boolean;
  sortOrder: number;
}

export interface ProductPayload {
  version: number | null;
  spuCode: string;
  name: string;
  alias: string | null;
  categoryId: number;
  description: string | null;
  status: ShelfStatus;
  skus: ProductSkuPayload[];
}
