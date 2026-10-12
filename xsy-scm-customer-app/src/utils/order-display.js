/*
 * 商城订单展示口径的唯一来源：展示分类文案、处理记录文案、状态语义档位。
 *
 * 展示分类（displayStatus）由服务端按「正式订单状态 + 配送履约事实」派生，客户端**不重新推导**，
 * 只做文案与配色映射；level 是语义档位（warn / primary / ok / off），由各页面映射成自己的 BEM 类名，
 * 这样列表与详情不共享类名，样式仍各自独立。
 */

/** 展示分类文案（与后端 MallOrderDisplayStatusEnum 一一对应） */
const DISPLAY_STATUS_MAP = {
  PENDING: { text: '待处理', level: 'warn' },
  PREPARING: { text: '备货中', level: 'primary' },
  DELIVERING: { text: '配送中', level: 'primary' },
  COMPLETED: { text: '已完成', level: 'ok' },
  CANCELLED: { text: '已取消', level: 'off' },
};

/** 列表页签；value 为空表示「全部」 */
export const ORDER_TABS = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PREPARING', label: '备货中' },
  { value: 'DELIVERING', label: '配送中' },
  { value: 'COMPLETED', label: '已完成' },
];

/** 取消原因快捷选项（仍允许自定义填写） */
export const CANCEL_REASONS = ['下错单了', '数量需要调整', '暂时不需要了', '配送时间不合适', '其他原因'];

/** 订单处理记录文案（与后端 OrderOperationType 一致；未覆盖的类型原样展示） */
const OPERATION_MAP = {
  CREATE: '提交订单',
  SUBMIT: '订单已受理',
  ACTUAL_QUANTITY: '录入实重',
  CONFIRM: '订单已确认',
  CREDIT_OVERRIDE: '超额授信放行',
  CANCEL: '订单已取消',
  RESERVE_STOCK: '已预留库存',
  UPDATE: '订单已修改',
  DELETE: '订单已删除',
};

export function displayStatusText(status) {
  return DISPLAY_STATUS_MAP[status]?.text || '处理中';
}

export function displayStatusLevel(status) {
  return DISPLAY_STATUS_MAP[status]?.level || 'off';
}

export function operationText(operationType) {
  return OPERATION_MAP[operationType] || operationType || '状态更新';
}

/**
 * 配送异常（拒收 / 破损 / 缺货争议等）。
 *
 * 展示分类暂时仍归入「已完成」——异常单必须有归属页签，否则客户在列表里找不到它；
 * 但是否应该单独出一个状态与页签，属产品口径，待第二阶段配送功能统一裁决。
 * 在那之前至少不能只说「已完成」：列表打标、详情给横幅，把真实履约状态说清楚。
 */
export function isAbnormalFulfillment(fulfillmentStatus) {
  return fulfillmentStatus === 'EXCEPTION';
}

/** 数量按四位小数定点做整数运算，去掉无意义的尾随零（与购物车、结算页同一口径） */
export function displayQuantity(quantity) {
  if (quantity === null || quantity === undefined || quantity === '') return '—';
  const scaled = Math.round(Number(quantity) * 10000);
  const int = Math.floor(scaled / 10000);
  const frac = String(scaled % 10000)
    .padStart(4, '0')
    .replace(/0+$/, '');
  return frac ? `${int}.${frac}` : `${int}`;
}

/** 金额展示：服务端下发四位小数字符串，空值显示 —，不把 null 当 0 */
export function displayAmount(amount) {
  if (amount === null || amount === undefined || amount === '') return '—';
  return String(amount);
}

export function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '';
}

export function formatDate(value) {
  return value ? String(value).slice(0, 10) : '';
}
