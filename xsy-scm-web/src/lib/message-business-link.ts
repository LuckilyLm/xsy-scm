/* 站内消息 → 业务单据跳转解析（纯函数，便于单测）。只产出站内路由，权限仍由目标页校验。 */

/** 与后端 `MessageTypeEnum.SCM_INVENTORY_LOSS_GAIN` 同值。 */
export const MESSAGE_TYPE_INVENTORY_LOSS_GAIN = 3;

/** 与后端 `MessageTypeEnum.SCM_INVENTORY_WARNING` 同值。 */
export const MESSAGE_TYPE_INVENTORY_WARNING = 4;

/** 报损报溢单列表路由，与后端 `ScmTodoCardEnum` 的待办路由同一入口。 */
export const INVENTORY_LOSS_GAIN_LIST_PATH = '/inventory/inventory-loss-gain-list';

/** 库存预警列表路由；消息的 `dataId` 是阈值配置 id。 */
export const INVENTORY_WARNING_LIST_PATH = '/inventory/inventory-warning-list';

export interface MessageBusinessLink {
  path: string;
  query: Record<string, string>;
  label: string;
}

/** 解析消息对应的业务入口；类型未知或 dataId 不是数字时返回 `undefined`。 */
export function messageBusinessLink(messageType: unknown, dataId: unknown): MessageBusinessLink | undefined {
  const id = typeof dataId === 'string' || typeof dataId === 'number' ? String(dataId).trim() : '';
  if (!/^\d{1,19}$/.test(id)) {
    return undefined;
  }
  if (Number(messageType) === MESSAGE_TYPE_INVENTORY_LOSS_GAIN) {
    return {path: INVENTORY_LOSS_GAIN_LIST_PATH, query: {id}, label: '查看业务单据'};
  }
  if (Number(messageType) === MESSAGE_TYPE_INVENTORY_WARNING) {
    return {path: INVENTORY_WARNING_LIST_PATH, query: {thresholdId: id}, label: '查看库存预警'};
  }
  return undefined;
}
