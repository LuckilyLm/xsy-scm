import type {ScmId, ScmSortItem} from './customer';

export type PriceSource = 'AGREEMENT' | 'CUSTOMER_GROUP' | 'CUSTOMER_TYPE' | 'TIME_PRICE' | 'MARKET';
export type AgreementFormulaType = 'FIXED' | 'MARKET_PLUS_AMOUNT' | 'COST_MARKUP_RATE' | 'MARKET_MARKUP_RATE' | 'PURCHASE_COST_LOSS_TAX_MARKUP';
export type UnavailableReason = 'SKU_NOT_FOUND' | 'SKU_OFF_SHELF' | 'SPU_OFF_SHELF' | 'CATEGORY_DISABLED' | 'NOT_VISIBLE';

export interface PriceForm {
  agreementPriceId?: ScmId;
  version?: number;
  customerId?: ScmId;
  skuId?: ScmId;
  unitPrice: string | null;
  formulaType?: AgreementFormulaType;
  formulaParameter?: string | null;
  effectiveFrom: string;
  effectiveTo: string | null;
}

export interface PriceRow extends PriceForm {
  customerCode?: string;
  customerName?: string;
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
  skuId?: ScmId;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  sortItemList?: ScmSortItem[];
}

/** 客户分组价：按「分组 + 商品 + 销售单位」维护；skuId 只用于确定商品与单位。 */
export interface GroupPriceForm {
  groupPriceId?: ScmId;
  version?: number;
  groupId?: ScmId;
  skuId?: ScmId;
  unitPrice: string | null;
  effectiveFrom: string;
  effectiveTo: string | null;
}

export interface GroupPriceRow extends GroupPriceForm {
  groupCode?: string;
  groupName?: string;
  spuId?: ScmId;
  productName?: string;
  saleUnit?: string;
  updatedAt?: string;
}

export interface GroupPriceQuery {
  pageNum: number;
  pageSize: number;
  groupId?: ScmId;
  spuId?: ScmId;
  keyword?: string;
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
  /** 销售单位；录单页用来判断参考价与当前行是否同一单位。 */
  saleUnit: string | null;
  unitPrice: string | null;
  priceStatus: 'PRICED' | 'UNPRICED';
  priceSource: PriceSource | null;
  sourceRecordId: ScmId | null;
  unpricedReason: 'NO_PRICE_SOURCE' | 'AGREEMENT_COST_MISSING' | 'AGREEMENT_MARKET_PRICE_MISSING' | 'AGREEMENT_RESULT_INVALID' | null;
  sellable: boolean;
  unavailableReason: UnavailableReason | null;
}

export interface ResolveResult {
  customerId: ScmId;
  at: string;
  items: ResolvedPrice[];
}

export interface HistoryRow {
  historyId: ScmId;
  source: string;
  priceId: ScmId;
  customerName: string;
  groupName?: string | null;
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

export interface CustomerVisibilitySummary {
  customerId: ScmId;
  customerCode: string;
  customerName: string;
  visibilityPolicy: string;
  productCount: number;
  specCount: number;
}

export interface VisibilityRow {
  customerId: ScmId;
  customerCode: string;
  customerName: string;
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
