/**
 * P1 分拣管理前端常量（新增文件）。
 *
 * 枚举值与后端**逐字对应**，且与 DB 的 CHECK 白名单同源：
 * - `ScmSortingTaskStatusEnum` / `SortingConstant` ↔ `ck_sorting_task_status`
 * - 明细结果 ↔ `ck_sorting_task_item_result`，占用位 ↔ `ck_sorting_task_item_occupation`
 * - 商品类型是订单行快照（`product_type`），不是分拣自己的状态
 *
 * **新增取值时必须同时改三处**：本文件、后端枚举 / `SortingConstant`、DB CHECK 白名单。
 * 漏掉最后一项的表现是「值写进去了，按它筛选时列表恒空」，比编译失败难发现得多。
 *
 * 本域不复用库存 / 采购的枚举：分拣状态与报损报溢的 `PENDING / COMPLETED` 只是字面相同，
 * 迁移含义完全不同（分拣的 `PENDING` 是「还没开始录」，报损报溢的 `PENDING` 是「等人审批」）。
 */
import type {SmartEnum} from '/@/types/smart-enum';

/**
 * 分拣任务状态（四值，裁决补充第 16 条）。
 *
 * ```text
 * PENDING ──首次录入──→ SORTING ──全部行有结果──→ COMPLETED ──重开（保留已录内容）──→ SORTING
 *    │                     │
 *    └──────取消───────────┴──→ CANCELLED（同时释放全部明细占用位）
 * ```
 *
 * `CANCELLED` 不再改动；`COMPLETED` 只能走重开，不能直接取消 —— 否则「完成」这一事实会被静默抹掉。
 */
export const SCM_SORTING_TASK_STATUS_ENUM: SmartEnum<string> = {
    PENDING: {value: 'PENDING', desc: '待分拣'},
    SORTING: {value: 'SORTING', desc: '分拣中'},
    COMPLETED: {value: 'COMPLETED', desc: '已完成'},
    CANCELLED: {value: 'CANCELLED', desc: '已取消'},
};

/**
 * 明细行分拣结果（非标品这一层录的是实重，P1 不做单位换算，也不记毛重 / 皮重 / 净重）。
 *
 * 结果为 0 是合法的（整行缺货），所以「缺货」必须能被显式表达 ——
 * 靠「不提交这一行」表达缺货会让任务永远无法完成。
 */
export const SCM_SORTING_RESULT_ENUM: SmartEnum<string> = {
    NORMAL: {value: 'NORMAL', desc: '正常'},
    SHORT: {value: 'SHORT', desc: '少拣'},
    OUT_OF_STOCK: {value: 'OUT_OF_STOCK', desc: '缺货'},
    OVER: {value: 'OVER', desc: '多拣'},
};

/** 商品类型快照：标品按订购量核对，非标品以这里录入的实重为准。 */
export const SCM_SORTING_PRODUCT_TYPE_ENUM: SmartEnum<string> = {
    STANDARD: {value: 'STANDARD', desc: '标品'},
    NON_STANDARD: {value: 'NON_STANDARD', desc: '非标品'},
};

/**
 * 明细占用位（与任务状态**不是一回事**，别混用）。
 *
 * 活动占用下「订单行 → 分拣任务」由部分唯一索引保证一对一；
 * 取消释放后同一订单行可以进入新任务，历史行仍随详情返回以便追溯。
 */
export const SCM_SORTING_OCCUPATION_ENUM: SmartEnum<string> = {
    ACTIVE: {value: 'ACTIVE', desc: '占用中'},
    RELEASED: {value: 'RELEASED', desc: '已释放'},
};

/** 状态对应的 `a-tag` 颜色：进行中的蓝、可继续作业的黄、终态绿、失效灰。 */
export const SCM_SORTING_TASK_STATUS_COLOR: Record<string, string> = {
    PENDING: 'orange',
    SORTING: 'blue',
    COMPLETED: 'green',
    CANCELLED: 'default',
};

/** 结果对应的 `a-tag` 颜色：异常用暖色，让差异行在长表里一眼可辨。 */
export const SCM_SORTING_RESULT_COLOR: Record<string, string> = {
    NORMAL: 'green',
    SHORT: 'orange',
    OUT_OF_STOCK: 'red',
    OVER: 'purple',
};

export const SCM_SORTING_PRODUCT_TYPE_COLOR: Record<string, string> = {
    STANDARD: 'default',
    NON_STANDARD: 'cyan',
};

export const SCM_SORTING_OCCUPATION_COLOR: Record<string, string> = {
    ACTIVE: 'blue',
    RELEASED: 'default',
};

/**
 * 分拣域权限码（与后端 `@SaCheckPermission` 与 V61 的 `web_perms` 逐字一致）。
 *
 * 两条不是显而易见的语义：
 * - `scm:sorting:task:assign` **隐含跨指派人可见**：持者才能建单、指派 / 改派、取消与重开，
 *   也只有他能按人筛选与看未指派队列；分拣员只看到派给自己的任务。因此前端用它作为
 *   「队列管理者」开关，不再另设 `*:scope:all:query`。
 * - 录入（`scm:sorting:item:update`）与完成只认受指派人本人，权限码之外服务端还会比对身份；
 *   前端据此把非受指派人的编辑区渲染成只读，而不是显示了输入框等提交后被 30005 拒绝。
 */
export const SCM_SORTING_PERMISSION = {
    TASK_QUERY: 'scm:sorting:task:query',
    TASK_ADD: 'scm:sorting:task:add',
    TASK_ASSIGN: 'scm:sorting:task:assign',
    ITEM_UPDATE: 'scm:sorting:item:update',
    TASK_COMPLETE: 'scm:sorting:task:complete',
    TASK_CANCEL: 'scm:sorting:task:cancel',
    TASK_REOPEN: 'scm:sorting:task:reopen',
    TASK_PRINT: 'scm:sorting:task:print',
    SUMMARY_QUERY: 'scm:sorting:summary:query',
} as const;

/**
 * 表格 DOM id —— **给 Playwright 定位用**，不是 `TableOperator` 的 `tableId`。
 *
 * 本阶段未向 `TABLE_ID_CONST.BUSINESS` 注册数字 id，因此页面不提供列配置入口（`TableOperator`）；
 * 注册属于底座常量文件，不在这次改动范围内。
 */
export const SCM_SORTING_TABLE_ID = {
    TASK: 'scm-sorting-task-table',
    TASK_ITEM: 'scm-sorting-task-item-table',
    CANDIDATE_LINE: 'scm-sorting-candidate-line-table',
    SUMMARY: 'scm-sorting-summary-table',
} as const;

/** 可出单状态：待分拣还没开始、已取消不再出单，两者都不给打印（与 `SortingConstant.PRINTABLE` 同集合）。 */
export const SCM_SORTING_PRINTABLE_STATUS: readonly string[] = ['SORTING', 'COMPLETED'];

/** 还能干活的状态：录入、完成、指派都以此为准（与 `SortingConstant.WORKING` 同集合）。 */
export const SCM_SORTING_WORKING_STATUS: readonly string[] = ['PENDING', 'SORTING'];

/** 分页上限：后端 `SortingQueryService.page()` 对 `pageSize > 100` 直接回 30001。 */
export const SCM_SORTING_MAX_PAGE_SIZE = 100;

export default {
    // 只导出**枚举**：`SCM_SORTING_TABLE_ID` / `SCM_SORTING_PERMISSION` 不是枚举，
    // 混进 `constantsInfo` 会让 `$smartEnumPlugin.getValueDescList` 拿到非枚举对象。
    SCM_SORTING_TASK_STATUS_ENUM,
    SCM_SORTING_RESULT_ENUM,
    SCM_SORTING_PRODUCT_TYPE_ENUM,
    SCM_SORTING_OCCUPATION_ENUM,
};
