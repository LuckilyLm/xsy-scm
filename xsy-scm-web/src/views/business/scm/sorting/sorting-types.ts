/** 分拣数量用四位小数字符串：null 表示未录入，"0.0000" 表示已录入零。任务和明细各自校验版本。分拣只记录作业数量，不修改订单或库存。 */
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';

/** 后端主键是 `Long`，经 JSON 数字或字符串抵达前端；一律不参与算术。 */
export type Id = string | number;

/** 分拣任务状态：只有四值，取消即释放占用位，不存在 `RELEASED` 任务状态。 */
export type SortingTaskStatus = 'PENDING' | 'SORTING' | 'COMPLETED' | 'CANCELLED';

/** 明细行的分拣结果；非 `NORMAL` 时后端强制要求原因。 */
export type SortingLineResult = 'NORMAL' | 'SHORT' | 'OUT_OF_STOCK' | 'OVER';

/** 商品类型快照（不做单位换算，非标品这里录的是实重）。 */
export type SortingProductType = 'STANDARD' | 'NON_STANDARD';

/** 明细占用位：`ACTIVE` 才代表待办量；随取消释放的历史行仍随详情返回。 */
export type SortingOccupation = 'ACTIVE' | 'RELEASED';

/** 分页入参：`pageNum` / `pageSize` 必填，且 `pageSize` 上限 100（后端拒绝更大值）。 */
export interface SortingPage {
    pageNum: number;
    pageSize: number;
}

export interface SortingTaskQuery extends SortingPage {
    keyword?: string;
    status?: SortingTaskStatus;
    warehouseId?: Id;
    assigneeEmployeeId?: Id;
    unassignedOnly?: boolean;
    /** 送货时间区间（半开），按建单时冻结的快照过滤。 */
    deliveryTimeFrom?: string;
    deliveryTimeTo?: string;
    /** 预配送波次（线路维度由它表达）。 */
    deliveryWave?: string;
    /** 供应商来源，按任务上冻结的供应商过滤。 */
    supplierId?: Id;
}

export interface SortingSummaryQuery extends SortingPage {
    keyword?: string;
    warehouseId?: Id;
    taskStatus?: SortingTaskStatus;
    /** 建单时冻结的送货日，按 Asia/Shanghai 日期查询。 */
    deliveryDate?: string;
}

export interface SortingCandidateQuery extends SortingPage {
    keyword?: string;
    customerId?: Id;
    orderId?: Id;
}

/** `SortingTaskVO`：列表行刻意不汇总量，一个任务里的行可以来自不同单位。 */
export interface SortingTask {
    id: Id;
    taskNo: string;
    warehouseId: Id;
    warehouseNameSnapshot: string;
    assigneeEmployeeId?: Id | null;
    assigneeName?: string | null;
    status: SortingTaskStatus;
    itemCount: number;
    processedCount: number;
    remark?: string | null;
    /** 建单时冻结的送货时间；筛选与展示都按它。 */
    deliveryTimeSnapshot?: string | null;
    /** 预配送波次；线路维度由它表达。 */
    deliveryWave?: string | null;
    /** 建单时显式指定的供应商来源与名称快照。 */
    supplierId?: Id | null;
    supplierNameSnapshot?: string | null;
    createdAt: string;
    startedAt?: string | null;
    completedAt?: string | null;
    cancelledAt?: string | null;
    printCount: number;
    lastPrintedAt?: string | null;
    version: number;
}

/** 分拣清单行的来源：订单行 / 满赠赠品权益。 */
export type SortingItemSourceType = 'ORDER_ITEM' | 'PROMOTION_GIFT';

/** 分拣明细的计划量在建单时确定。订单行与促销赠品分开读取；赠品不记录订单行 ID，也不回写分拣量。 */
export interface SortingTaskItem {
    id: Id;
    taskId: Id;
    salesOrderId: Id;
    /** 赠品行恒为 null：赠品不挂订单行。 */
    salesOrderItemId: Id | null;
    orderNoSnapshot: string;
    customerId: Id;
    customerNameSnapshot: string;
    spuId: Id;
    skuId: Id;
    spuCodeSnapshot: string;
    productNameSnapshot: string;
    skuCodeSnapshot?: string | null;
    specNameSnapshot?: string | null;
    saleUnitSnapshot: string;
    productTypeSnapshot?: SortingProductType | string | null;
    /** 赠品行装的是赠品数量（冻结权益）。 */
    plannedQuantitySnapshot: string | null;
    /** 赠品行恒为 null。 */
    sortedQuantity: string | null;
    result?: SortingLineResult | null;
    reason?: string | null;
    sortedBy?: string | null;
    sortedAt?: string | null;
    occupationStatus: SortingOccupation;
    version: number;
    /** 缺省按订单行处理，兼容升级前返回的数据。 */
    sourceType?: SortingItemSourceType | null;
}

/** 分次登记只追加待确认事件；最终数量必须显式确认，正式出库不读取这些事件。 */
export interface SortingPartPayload {
    taskItemId: Id;
    version: number;
    quantity: string;
    note?: string;
}

export interface SortingPartRecord {
    id: Id;
    taskId: Id;
    taskItemId: Id;
    eventType: 'APPEND' | 'SET_TOTAL';
    quantityDelta: string;
    totalQuantity: string;
    note?: string | null;
    createdBy: string;
    createdAt: string;
}

export interface SortingPartHistory {
    records: SortingPartRecord[];
    totalQuantity: string | null;
    hasMore: boolean;
    nextBeforeId: Id | null;
}

/** `SortingTaskDetailVO`。 */
export interface SortingTaskDetail {
    task: SortingTask;
    items: SortingTaskItem[];
}

/** `SortingSkuSummaryVO`：分组键含销售单位，因此同一行内的量天然同单位。 */
export interface SortingSkuSummary {
    skuId: Id;
    spuCodeSnapshot: string;
    productNameSnapshot: string;
    skuCodeSnapshot?: string | null;
    specNameSnapshot?: string | null;
    saleUnitSnapshot: string;
    lineCount: number;
    orderCount: number;
    taskCount: number;
    plannedQuantity: string | null;
    sortedQuantity: string | null;
    unprocessedCount: number;
}

/** 绩效只指最终确认行，不能混同为实际分次劳动量或工资依据。 */
export interface SortingPerformance {
    warehouseId: Id;
    warehouseNameSnapshot: string;
    confirmedBy: string;
    saleUnitSnapshot: string;
    taskCount: number;
    lineCount: number;
    confirmedQuantity: string;
    lastConfirmedAt?: string | null;
}

/** 按客户逐条展示已占用的订单分拣明细，不跨销售单位计算合计。 */
export interface SortingCustomerLine extends SortingTaskItem {
    taskNo: string;
    taskStatus: SortingTaskStatus;
    warehouseNameSnapshot: string;
    deliveryTimeSnapshot?: string | null;
}

/** `SortingCandidateLineVO`：可进入分拣的已确认订单明细。 */
export interface SortingCandidateLine {
    salesOrderItemId: Id;
    salesOrderId: Id;
    orderNo: string;
    customerId: Id;
    customerName: string;
    spuId: Id;
    skuId: Id;
    spuCodeSnapshot: string;
    productNameSnapshot: string;
    skuCodeSnapshot?: string | null;
    specNameSnapshot?: string | null;
    saleUnitSnapshot: string;
    productTypeSnapshot?: SortingProductType | string | null;
    orderedQuantity: string | null;
    actualQuantity: string | null;
    confirmedAt: string;
    expectDeliveryTime?: string | null;
}

/** `SortingPrintVO`：预览内容与正式生成同源，但预览既不改状态也不计次。 */
export interface SortingPrint {
    taskId: Id;
    taskNo: string;
    warehouseNameSnapshot: string;
    assigneeName?: string | null;
    status: SortingTaskStatus;
    printCount: number;
    generatedAt: string;
    items: SortingTaskItem[];
}

/** `SortingPrintResultVO`：登记打印只回计次、时间与操作对象，没有任何库存或状态变化。 */
export interface SortingPrintResult {
    taskId: Id;
    taskNo: string;
    itemCount: number;
    printCount: number;
    generatedAt: string;
}

/**
 * `SortingTaskCreateForm`：仓库由操作人显式选择，不从订单 / 客户 / 线路推断。
 *
 * `assigneeEmployeeId` 收窄成 `number`：原生 `EmployeeSelect` 的 `value` prop 声明为
 * `[Number, Array]`，传 `string | number` 在 vue-tsc 下会报 TS2322；后端本来就是 `Long`。
 */
export interface SortingTaskCreatePayload {
    warehouseId: Id;
    assigneeEmployeeId?: number | null;
    remark?: string | null;
    salesOrderItemIds: Id[];
}

/** `SortingAssignForm`：改派保留已录入的分拣量，只换受指派人。 */
export interface SortingAssignPayload {
    assigneeEmployeeId: number;
    version: number;
    reason?: string | null;
}

/**
 * `SortingEntryItemForm`：一行明细的分拣结果。
 *
 * `sortedQuantity` 以字符串提交（4 位定点），`version` 是这一行读到的版本 ——
 * 一次批量录入里某行被他人改过只该顶掉那一行所在的这次提交，不能被任务版本掩盖。
 */
export interface SortingEntryItemPayload {
    id: Id;
    version: number;
    sortedQuantity: string;
    result: SortingLineResult;
    reason?: string | null;
}

/** `SortingEntryForm`：允许一次只处理任务里的部分明细（边称边录是常态）。 */
export interface SortingEntryPayload {
    items: SortingEntryItemPayload[];
}

/** `SortingActionForm`：完成 / 取消 / 重开 / 正式打印共用。 */
export interface SortingActionPayload {
    version: number;
    reason?: string | null;
}

export type SortingResponse<T> = ScmResponse<T>;
export type SortingPageResponse<T> = ScmResponse<ScmPage<T>>;

/**
 * 后端消息提取：范围守卫拒绝（30005）与状态机拒绝（41121）都必须把服务端 `msg` 原样显示，
 * 前端不改写成人话 —— 改写会让「你无权操作」与「状态不允许」看起来是同一件事。
 */
export function sortingError(error: unknown): string {
    const response = error as {
        data?: {msg?: string};
        response?: {data?: {msg?: string}};
        message?: string;
    };
    return response?.data?.msg ?? response?.response?.data?.msg ?? response?.message ?? '操作失败，请刷新后重试';
}

/** 定点数量文本：`null` 显示破折号，其余原样透传，不补零、不换算精度。 */
export function quantityText(value: string | null | undefined): string {
    return value === null || value === undefined || value === '' ? '—' : value;
}

// ------------------------------------------------------------------
// 电子秤读数（ADM-11）
// ------------------------------------------------------------------

export type SortingScaleStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

/** 电子秤事件分别返回原始读数和人工接受数量。设备采集时间与服务端接收时间不同，重复上报不代表处理失败。 */
export interface SortingScaleEvent {
    id: Id;
    eventKey: string;
    taskId: Id;
    taskItemId: Id;
    skuId?: Id | null;
    skuCodeSnapshot?: string | null;
    productNameSnapshot?: string | null;
    deviceCode: string;
    rawReading?: string | null;
    unit: string;
    stableFlag: boolean;
    capturedAt: string;
    receivedAt: string;
    status: SortingScaleStatus;
    acceptedQuantity?: string | null;
    acceptedAt?: string | null;
    acceptedBy?: string | null;
    rejectedAt?: string | null;
    rejectedBy?: string | null;
    rejectReason?: string | null;
    version: number;
    duplicated?: boolean;
}

export const sortingScaleStatuses: Record<SortingScaleStatus, { label: string; color: string }> = {
    PENDING: {label: '待处理', color: 'blue'},
    ACCEPTED: {label: '已接受', color: 'green'},
    REJECTED: {label: '已驳回', color: 'default'},
};

export interface SortingStandardItem {
    id: Id;
    version: number;
    orderNo: string;
    customerName: string;
    productName: string;
    specName: string;
    quantity: string | null;
    unit: string;
    eligible: boolean;
    reason: string | null;
}
export interface SortingStandardPreview {
    taskId: Id;
    version: number;
    items: SortingStandardItem[];
}
export interface SortingStandardPayload {
    version: number;
    items: {id: Id; version: number}[];
}
export interface SortingStandardResult {
    taskId: Id;
    confirmedItemIds: Id[];
}
