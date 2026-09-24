/**
 * P1 分拣管理前端契约（与后端 `module/scm/sorting/domain/{form,vo}` 逐字对齐）。
 *
 * 三条口径：
 * - 数量（`BigDecimal` + `ScmFixedScale4Serializer`）到前端一律是 **4 位定点字符串**或 `null`；
 *   `null` 是「尚未录入」，`"0.0000"` 是「录入过且为 0（整行缺货）」，两者绝不合并渲染。
 *   因此这里不出现 `number` 类型的量，页面也不得 `Number()` / `toFixed()` 后再算。
 * - 计数（`itemCount` / `printCount` 等 `Integer`）才是 JSON 数字。
 * - 每个任务与每条明细各带自己的 `version`：录入按行提交，所以行版本不能被任务版本替代。
 *
 * 分拣是**独立的实发事实**：不回写订单实发量、不改结算金额、不写库存余额与流水
 * （裁决第 1、3 条）。任何把本页数字当成「订单已改成这个数」的命名都属于口径漂移。
 */
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';

/** 后端主键是 `Long`，经 JSON 数字或字符串抵达前端；一律不参与算术。 */
export type Id = string | number;

/** 分拣任务状态：只有四值，取消即释放占用位，不存在 `RELEASED` 任务状态。 */
export type SortingTaskStatus = 'PENDING' | 'SORTING' | 'COMPLETED' | 'CANCELLED';

/** 明细行的分拣结果；非 `NORMAL` 时后端强制要求原因。 */
export type SortingLineResult = 'NORMAL' | 'SHORT' | 'OUT_OF_STOCK' | 'OVER';

/** 商品类型快照（P1 不做单位换算，非标品这里录的是实重）。 */
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
}

export interface SortingSummaryQuery extends SortingPage {
    keyword?: string;
    warehouseId?: Id;
    taskStatus?: SortingTaskStatus;
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
    createdAt: string;
    startedAt?: string | null;
    completedAt?: string | null;
    cancelledAt?: string | null;
    printCount: number;
    lastPrintedAt?: string | null;
    version: number;
}

/** `SortingTaskItemVO`：计划量是建单时冻结的订单行实发量，之后订单怎么改都不追溯。 */
export interface SortingTaskItem {
    id: Id;
    taskId: Id;
    salesOrderId: Id;
    salesOrderItemId: Id;
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
    plannedQuantitySnapshot: string | null;
    sortedQuantity: string | null;
    result?: SortingLineResult | null;
    reason?: string | null;
    sortedBy?: string | null;
    sortedAt?: string | null;
    occupationStatus: SortingOccupation;
    version: number;
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
 * `sortedQuantity` 以字符串提交（4 位定点），`version` 是**这一行**读到的版本 ——
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
