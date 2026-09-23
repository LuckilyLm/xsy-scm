/*
 * 站内消息 → 业务单据的跳转解析（纯函数，零运行时依赖，便于单测）。
 *
 * 业务类型只认后端 `MessageTypeEnum` 的**数值**（消息 VO 的 `messageType` 就是该数值），
 * 不解析中文标题：标题是可随意改动的文案，用它判断业务会把跳转悄悄绑死在措辞上。
 * 复用原生 `t_message.message_type` + `data_id`，不新建第二套消息中心、不加列。
 *
 * 这里只产出**站内路由**，不直接调用业务接口：目标页面的列表 / 详情接口自带
 * `scm:inventory:loss-gain:*` 校验，因此用户后来失去权限时旧消息照样跳不出数据（仍 403）。
 */

/** 与后端 `MessageTypeEnum.SCM_INVENTORY_LOSS_GAIN` 同值；两侧一致性由 w4 消息跳转单测核对。 */
export const MESSAGE_TYPE_INVENTORY_LOSS_GAIN = 3;

/** 报损报溢单列表路由，与后端 `ScmTodoCardEnum` 的待办路由同一入口。 */
export const INVENTORY_LOSS_GAIN_LIST_PATH = '/inventory/inventory-loss-gain-list';

export interface MessageBusinessLink {
  path: string;
  query: Record<string, string>;
  label: string;
}

/**
 * 解析消息对应的业务单据入口；无法识别的业务类型或缺少可用主键时返回 `undefined`（不显示入口）。
 *
 * `dataId` 必须是纯数字串：通用站内信与历史消息的 `dataId` 可能为空、为 `"null"`（原生
 * `MessageService` 对 null 做过 `String.valueOf`）或根本不是主键，跳过去只会得到一个查不到东西的页面。
 */
export function messageBusinessLink(messageType: unknown, dataId: unknown): MessageBusinessLink | undefined {
  const id = typeof dataId === 'string' || typeof dataId === 'number' ? String(dataId).trim() : '';
  if (!/^\d{1,19}$/.test(id)) {
    return undefined;
  }
  if (Number(messageType) === MESSAGE_TYPE_INVENTORY_LOSS_GAIN) {
    return {path: INVENTORY_LOSS_GAIN_LIST_PATH, query: {id}, label: '查看业务单据'};
  }
  return undefined;
}
