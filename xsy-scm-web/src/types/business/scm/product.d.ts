export type ProductId = string | number;
export type ShelfStatus = 'ON_SHELF' | 'OFF_SHELF';
export type ProductType = 'STANDARD' | 'NON_STANDARD';
/** 主档生命周期，与 status（是否在售）正交；ARCHIVED 不允许与 ON_SHELF 并存。 */
export type MasterStatus = 'ENABLED' | 'DISABLED' | 'ARCHIVED';
export type StorageMethod = 'AMBIENT' | 'CHILLED' | 'FROZEN';
export type AssistantStatus = 'ENABLED' | 'DISABLED';
export type UomCategory = 'WEIGHT' | 'COUNT' | 'VOLUME' | 'LENGTH' | 'OTHER';
/** 批量打标模式：ADD 追加 / REMOVE 摘除 / REPLACE 覆盖（空集合即清空）。 */
export type TagMode = 'ADD' | 'REMOVE' | 'REPLACE';

export interface ScmResponse<T> {
    code: number;
    ok: boolean;
    msg: string;
    data: T
}

export interface ProductPage<T> {
    pageNum: number;
    pageSize: number;
    total: number;
    pages: number;
    list: T[];
    emptyFlag: boolean
}

export interface ProductCategory {
    categoryId: ProductId;
    version: number;
    parentId?: ProductId | null;
    categoryCode: string;
    name: string;
    level: number;
    sortOrder: number;
    status: 'ENABLED' | 'DISABLED';
    categoryPath?: string;
    children?: ProductCategory[];
}

export interface ProductCategoryForm {
    categoryId?: ProductId;
    version?: number;
    parentId?: ProductId | null;
    categoryCode: string;
    name: string;
    sortOrder: number;
    status: 'ENABLED' | 'DISABLED';
}

export interface ProductSku {
    skuId?: ProductId;
    version?: number;
    skuCode: string;
    barcode?: string | null;
    specName: string;
    specValues: Record<string, string>;
    saleUnit: string;
    productType: ProductType;
    marketPrice: string;
    status: ShelfStatus;
    defaultFlag: boolean;
    sortOrder: number;
}

export interface ProductImage {
    imageId?: ProductId;
    version?: number;
    fileKey: string;
    fileUrl?: string;
    fileName?: string;
    fileSize?: number;
    primaryFlag: boolean;
    sortOrder: number;
}

/** 商品行上的标签摘要；停用的历史标签照样返回，只影响能否新挂。 */
export interface ProductTagRef {
    tagId: ProductId;
    tagCode: string;
    name: string;
    status: AssistantStatus;
}

/** 主档扩展字段：保质期/损耗/税务只在详情 VO 出现，列表 VO 只带前置的常用字段。 */
interface ProductMasterFields {
    mnemonicCode?: string | null;
    brandName?: string | null;
    origin?: string | null;
    storageMethod?: StorageMethod | null;
    shelfLifeDays?: number | null;
    lossRate?: number | null;
    purchaseWarningDays?: number | null;
    invoiceName?: string | null;
    taxCategoryCode?: string | null;
    taxExempt?: boolean;
    taxRate?: number | null;
}

export interface ProductForm extends ProductMasterFields {
    spuId?: ProductId;
    version?: number;
    spuCode: string;
    name: string;
    alias?: string | null;
    categoryId?: ProductId;
    description?: string | null;
    status: ShelfStatus;
    skuList: ProductSku[];
    images: ProductImage[];
    /** null 表示新增时取 ENABLED、编辑时保持原值。 */
    masterStatus?: MasterStatus | null;
    /** 提交时的标签全集：编辑接口按它整批替换，漏传等于清空标签。 */
    tagIds: ProductId[];
}

export interface ProductRow extends ProductMasterFields {
    spuId: ProductId;
    version: number;
    spuCode: string;
    name: string;
    alias?: string | null;
    categoryId?: ProductId;
    description?: string | null;
    status: ShelfStatus;
    categoryPath: string;
    categoryName: string;
    skuCount: number;
    masterStatus: MasterStatus;
    tags: ProductTagRef[];
    skuList: ProductSku[];
    images?: ProductImage[];
    defaultSku?: ProductSku;
    minMarketPrice: string | null;
    maxMarketPrice: string | null;
    primaryImageUrl?: string;
    updatedAt: string;
    createdAt?: string;
}

export interface ProductQuery {
    pageNum: number;
    pageSize: number;
    searchCount?: boolean;
    keyword?: string;
    categoryId?: ProductId;
    status?: ShelfStatus;
    skuStatus?: ShelfStatus;
    productType?: ProductType;
    masterStatus?: MasterStatus;
    storageMethod?: StorageMethod;
    tagIds?: ProductId[];
    hasPrimaryImage?: boolean;
    hasBarcode?: boolean;
    createdFrom?: string | null;
    createdTo?: string | null;
    sortItemList?: { column: string; isAsc: boolean }[];
}

/** 辅助资料列表条件：字典规模有限，后端不启用分页。 */
export interface AssistantQuery {
    keyword?: string;
    status?: AssistantStatus
}

export interface ProductUom {
    uomId: ProductId;
    version: number;
    uomCode: string;
    name: string;
    category: UomCategory;
    precisionScale: number;
    status: AssistantStatus;
    sortOrder: number;
    referencedCount: number;
}

export interface ProductUomAddForm {
    uomCode: string;
    name: string;
    category: UomCategory;
    precisionScale: number;
    status: AssistantStatus;
    sortOrder: number;
}

/** 编辑表单不含编码与名称：单位按名称字符串记账，改名会让历史数据失去真值来源，只能停用旧单位再新建。 */
export interface ProductUomUpdateForm {
    uomId: ProductId;
    version: number;
    category: UomCategory;
    precisionScale: number;
    status: AssistantStatus;
    sortOrder: number;
}

export interface ProductTag {
    tagId: ProductId;
    version: number;
    tagCode: string;
    name: string;
    status: AssistantStatus;
    sortOrder: number;
    productCount: number;
}

export interface ProductTagForm {
    tagId?: ProductId;
    version?: number;
    tagCode: string;
    name: string;
    status: AssistantStatus;
    sortOrder: number;
}

/** 批量命令逐行带乐观锁版本，冲突时后端能指到具体商品。 */
export interface ProductBatchItem {
    spuId: ProductId;
    version: number
}

export interface ProductBatchResult {
    updatedCount: number;
    failedCount: number;
    failures: { spuId: ProductId; spuCode: string | null; reasonCode: number; reasonMsg: string }[];
}
