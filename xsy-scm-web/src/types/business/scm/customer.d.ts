import type {ScmLocation} from '/@/components/business/scm/map/types';
/**
 *  客户域前端契约（与后端 `module/scm/customer` 的 Form / VO 一一对应）。
 *
 * 约定（与 `product.d.ts` 一致）：
 * - 金额字段是4 位定点字符串（`"0.0000"`），不是 number；`null` 表示「未设置」，与 `"0.0000"` 语义不同。
 * - 枚举一律用后端字符串码（`POTENTIAL` / `GROUP` / `BY_AMOUNT` …），不用数字。
 * - `version` 是乐观锁版本，编辑 / 状态 / 删除都必须回传。
 */

import type {AreaColumns} from './area';

export type ScmId = string | number;

export type CustomerStatus = 'POTENTIAL' | 'COOPERATING' | 'SUSPENDED' | 'BLACKLIST';

export type SettleMode = 'INDEPENDENT' | 'GROUP';

export type CreditPeriodType = 'BY_AMOUNT' | 'BY_TIME';

export type CreditPeriodUnit = 'DAY' | 'MONTH';

export type EnableStatus = 'ENABLED' | 'DISABLED';

/** SmartAdmin 统一响应信封（拦截器已解包，此处仅用于类型标注）。 */
export interface ScmResponse<T> {
    code: number;
    ok: boolean;
    msg: string;
    data: T;
}

export interface ScmPage<T> {
    pageNum: number;
    pageSize: number;
    total: number;
    pages: number;
    list: T[];
    emptyFlag: boolean;
}

export interface ScmSortItem {
    column: string;
    isAsc: boolean;
}

// ---------------------------------------------------------------------------
// 客户类型（可维护字典，对应后端 customer_type 表）
// ---------------------------------------------------------------------------

export interface CustomerType {
    typeId: ScmId;
    version: number;
    typeCode: string;
    name: string;
    status: EnableStatus;
    createdAt?: string;
}

export interface CustomerTypeForm {
    typeId?: ScmId;
    version?: number;
    typeCode: string;
    name: string;
    status: EnableStatus;
}

export interface CustomerTypeQuery {
    pageNum: number;
    pageSize: number;
    searchCount?: boolean;
    keyword?: string;
    status?: EnableStatus;
    sortItemList?: ScmSortItem[];
}

// ---------------------------------------------------------------------------
// 客户
// ---------------------------------------------------------------------------

/** 新建 / 编辑表单模型（UI 侧）：比写请求体多一个只读的编码回显。 */
export interface CustomerFormModel extends Partial<AreaColumns>, ScmLocation {
    customerId?: ScmId;
    version?: number;
    /** 只读回显：编码由服务端生成，不进入写请求（见 `CustomerPayload`）。 */
    customerCode: string;
    name: string;
    customerTypeId?: ScmId;
    parentCustomerId?: ScmId | null;
    settlementCustomerId?: ScmId | null;
    /**
     * 业务员（员工）ID。
     *
     * 刻意不用 `ScmId` 而是 `number | null`：原生 `employee-select` 的 `value` prop 声明为
     * `[Number, Array]`，传 `string | number | null` 会在 vue-tsc 下报 TS2322。
     * 后端是 Java `Long`，JSON 里本来就是 number，所以收窄成 number 也更贴近事实。
     */
    sellerId?: number | null;
    supplierId?: ScmId | null;
    contactName?: string | null;
    contactPhone?: string | null;
    address?: string | null;
    settleMode: SettleMode;
    creditLimit?: string | null;
    creditPeriodType?: CreditPeriodType | null;
    creditAmountThreshold?: string | null;
    creditPeriodValue?: number | null;
    creditPeriodUnit?: CreditPeriodUnit | null;
    settleDay?: number | null;
    remark?: string | null;
}

/**
 * 写请求体（对应 CustomerAddForm / CustomerUpdateForm）。
 *
 * 刻意不含 `customerCode`：后端两个表单都没有这个字段，客户端带上它只会让「UI 能填、服务端不采信」
 * 变成两套真相。类型上摘掉之后，谁再把它拼回请求体都过不了编译。
 */
export type CustomerPayload = Omit<CustomerFormModel, 'customerCode'>;

/** 列表行（对应 CustomerVO）。 */
export interface CustomerRow extends CustomerFormModel {
    customerId: ScmId;
    version: number;
    status: CustomerStatus;
    customerTypeName?: string;
    parentCustomerName?: string;
    sellerName?: string;
    updatedAt: string;
}

/** 详情（对应 CustomerDetailVO，比列表行多出绑定供应商名与创建时间）。 */
export interface CustomerDetail extends CustomerRow {
    supplierName?: string;
    settlementCustomerName?: string;
    createdAt?: string;
}

/** 下拉选项（对应 CustomerOptionVO）。 */
export interface CustomerOption {
    customerId: ScmId;
    customerCode: string;
    name: string;
    status: CustomerStatus;
    customerTypeId?: ScmId | null;
    /** 客户类型编码（例如 `GROUP`），用于「上级集团客户」这类按类型收窄的选择器。 */
    customerTypeCode?: string | null;
}

export interface CustomerQuery {
    pageNum: number;
    pageSize: number;
    searchCount?: boolean;
    keyword?: string;
    customerTypeId?: ScmId;
    status?: CustomerStatus;
    settleMode?: SettleMode;
    parentCustomerId?: ScmId;
    sortItemList?: ScmSortItem[];
}

export interface CustomerStatusPayload {
    customerId: ScmId;
    version: number;
    status: CustomerStatus;
}

export interface CustomerDeletePayload {
    customerId: ScmId;
    version: number;
}

/**
 * 改派客户业务归属（对应后端 CustomerSellerReassignForm，POST /scm/customer/reassignSeller）。
 *
 * 与 CustomerFormModel 分离：归属只能通过本端点变更（/update 一律不动 seller_id），
 * 且必须携带读到的 CustomerRow.version 做乐观锁，`sellerId` 为 `null` 即收回为未分配。
 */
export interface CustomerSellerReassignPayload {
    customerId: ScmId;
    /** 新负责人（员工 id）；`null` 表示收回为未分配（未分配客户仅授权 / 全量范围者可见）。 */
    sellerId: number | null;
    version: number;
}

/**
 * 客户「常购商品」聚合行（对应后端 CustomerFrequentSkuVO， 只读）。
 *
 * 按 (skuId, unit) 分组：同 SKU 历史单位改变时分行展示，绝不跨单位求和。
 * orderedQuantity 是订购量（非实重 / 结算量）；recentUnitPrice 是最近已确认订单价，
 * 锁定单价缺失时为 null（前端不兜底为草稿价 / 当前价）。
 */
export interface CustomerFrequentSku {
    skuId: ScmId;
    skuCode: string | null;
    productName: string | null;
    specName: string | null;
    unit: string;
    orderCount: number;
    orderedQuantity: string;
    lastConfirmedAt: string;
    recentUnitPrice: string | null;
}
