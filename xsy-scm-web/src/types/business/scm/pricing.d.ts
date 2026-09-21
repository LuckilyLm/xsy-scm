import type {ScmId, ScmSortItem} from './customer';

export type PriceSource = 'AGREEMENT' | 'CUSTOMER_TYPE' | 'MARKET';
export type UnavailableReason =
    'SKU_NOT_FOUND'
    | 'SKU_OFF_SHELF'
    | 'SPU_OFF_SHELF'
    | 'CATEGORY_DISABLED'
    | 'NOT_VISIBLE';

export interface PriceForm {
    agreementPriceId?: ScmId;
    customerTypePriceId?: ScmId;
    version?: number;
    customerId?: ScmId;
    customerTypeId?: ScmId;
    skuId?: ScmId;
    unitPrice: string;
    effectiveFrom: string;
    effectiveTo: string | null;
}

export interface PriceRow extends PriceForm {
    customerCode?: string;
    customerName?: string;
    customerTypeCode?: string;
    customerTypeName?: string;
    skuCode: string;
    productName: string;
    specName: string;
    updatedAt: string;
}

export interface PriceQuery {
    pageNum: number;
    pageSize: number;
    keyword?: string;
    customerId?: ScmId;
    customerTypeId?: ScmId;
    skuId?: ScmId;
    effectiveFrom?: string | null;
    effectiveTo?: string | null;
    sortItemList?: ScmSortItem[];
}

export interface SkuOption {
    skuId: ScmId;
    skuCode: string;
    spuId: ScmId;
    productName: string;
    specName: string;
    specValues: Record<string, string>;
    saleUnit: string;
    productType: string;
    status: string;
    spuStatus: string;
    categoryStatus: string;
    marketPrice: string;
}

export interface ResolvedPrice {
    skuId: ScmId;
    skuCode: string | null;
    productName: string | null;
    specName: string | null;
    unitPrice: string | null;
    priceStatus: 'PRICED' | 'UNPRICED';
    priceSource: PriceSource | null;
    sourceRecordId: ScmId | null;
    unpricedReason: 'NO_PRICE_SOURCE' | null;
    sellable: boolean;
    unavailableReason: UnavailableReason | null;
}

export interface ResolveResult {
    customerId: ScmId;
    customerTypeId: ScmId;
    customerTypeName: string;
    at: string;
    items: ResolvedPrice[];
}

export interface BatchRow extends PriceForm {
    rowNumber: number;
}

export interface BatchFailure {
    rowNumber: number;
    customerTypeId: ScmId;
    skuId: ScmId;
    code: number;
    message: string;
}

export interface BatchResult {
    batchKey: string;
    committed: boolean;
    rowCount: number;
    ids: ScmId[];
    failures: BatchFailure[];
}

export interface HistoryRow {
    historyId: ScmId;
    source: string;
    priceId: ScmId;
    customerName: string;
    customerTypeName: string;
    skuCode: string;
    productName: string;
    specName: string;
    operationType: string;
    operator: string;
    operatedAt: string;
    beforeData: Record<string, unknown> | null;
    afterData: Record<string, unknown> | null;
    currentUnitPrice: string | null;
    currentEffectiveFrom: string | null;
    currentEffectiveTo: string | null;
    currentDeleted: boolean;
}

export interface VisibilityRow {
    customerId: ScmId;
    customerCode: string;
    customerName: string;
    customerTypeName: string;
    visibilityPolicy: string;
    skuId: ScmId | null;
    skuCode: string | null;
    productName: string | null;
    specName: string | null;
    skuStatus: string | null;
    spuStatus: string | null;
    createdAt: string | null;
    createdBy: string | null;
}
