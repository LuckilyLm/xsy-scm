/** 分拣状态、明细结果及占用标记与后端枚举和数据库约束保持一致。相同英文状态在其他业务中可能含义不同，不能复用枚举。 */
import type {SmartEnum} from '/@/types/smart-enum';

/** 分拣任务从待分拣到分拣中、已完成；已完成只能重开，不能直接取消。取消会释放订单行占用，历史记录保留。 */
export const SCM_SORTING_TASK_STATUS_ENUM: SmartEnum<string> = {
  PENDING: {value: 'PENDING', desc: '待分拣'},
  SORTING: {value: 'SORTING', desc: '分拣中'},
  COMPLETED: {value: 'COMPLETED', desc: '已完成'},
  CANCELLED: {value: 'CANCELLED', desc: '已取消'},
};

/**
 * 明细行分拣结果（非标品这一层录的是实重，不做单位换算，也不记毛重 / 皮重 / 净重）。
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
 * 明细占用位（与任务状态不是一回事，别混用）。
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

/** 分拣队列管理者可以跨指派人查看并分配任务；普通分拣员只能操作分配给自己的任务。前端隐藏不可用操作，后端仍须校验权限和操作人。 */
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
  /** 电子秤（ADM-11）：设备上报 / 待接受读数查询 / 人工接受 / 事件追溯，与 `SortingPermission` 同集合。 */
  SCALE_QUERY: 'scm:sorting:scale:query',
  SCALE_ACCEPT: 'scm:sorting:scale:accept',
  SCALE_REPORT: 'scm:sorting:scale:report',
} as const;

/**
 * 表格 DOM id —— 给 Playwright 定位用，不是 `TableOperator` 的 `tableId`。
 *
 * 未向 `TABLE_ID_CONST.BUSINESS` 注册数字 id，因此页面不提供列配置入口（`TableOperator`）。
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
  // 只导出枚举：`SCM_SORTING_TABLE_ID` / `SCM_SORTING_PERMISSION` 不是枚举，
  // 混进 `constantsInfo` 会让 `$smartEnumPlugin.getValueDescList` 拿到非枚举对象。
  SCM_SORTING_TASK_STATUS_ENUM,
  SCM_SORTING_RESULT_ENUM,
  SCM_SORTING_PRODUCT_TYPE_ENUM,
  SCM_SORTING_OCCUPATION_ENUM,
};
