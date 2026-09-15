export type ProductId = string | number;
export type ShelfStatus = 'ON_SHELF' | 'OFF_SHELF';
export type ProductType = 'STANDARD' | 'NON_STANDARD';
export interface ScmResponse<T> { code: number; ok: boolean; msg: string; data: T }
export interface ProductPage<T> { pageNum: number; pageSize: number; total: number; pages: number; list: T[]; emptyFlag: boolean }
export interface ProductCategory {
  categoryId: ProductId; version: number; parentId?: ProductId | null; categoryCode: string;
  name: string; level: number; sortOrder: number; status: 'ENABLED' | 'DISABLED'; categoryPath?: string; children?: ProductCategory[];
}
export interface ProductCategoryForm {
  categoryId?: ProductId; version?: number; parentId?: ProductId | null; categoryCode: string; name: string; sortOrder: number; status: 'ENABLED' | 'DISABLED';
}
export interface ProductSku {
  skuId?: ProductId; version?: number; skuCode: string; barcode?: string | null; specName: string;
  specValues: Record<string, string>; saleUnit: string; productType: ProductType; marketPrice: string;
  status: ShelfStatus; defaultFlag: boolean; sortOrder: number;
}
export interface ProductImage {
  imageId?: ProductId; version?: number; fileKey: string; fileUrl?: string; fileName?: string;
  fileSize?: number; primaryFlag: boolean; sortOrder: number;
}
export interface ProductForm {
  spuId?: ProductId; version?: number; spuCode: string; name: string; alias?: string | null;
  categoryId?: ProductId; description?: string | null; status: ShelfStatus; skuList: ProductSku[]; images: ProductImage[];
}
export interface ProductRow extends ProductForm {
  spuId: ProductId; version: number; categoryPath: string; categoryName: string; skuCount: number;
  defaultSku?: ProductSku; minMarketPrice: string | null; maxMarketPrice: string | null;
  primaryImageUrl?: string; updatedAt: string; createdAt?: string;
}
export interface ProductQuery {
  pageNum: number; pageSize: number; searchCount?: boolean; keyword?: string;
  categoryId?: ProductId; status?: ShelfStatus; skuStatus?: ShelfStatus; productType?: ProductType;
  sortItemList?: { column: string; isAsc: boolean }[];
}
