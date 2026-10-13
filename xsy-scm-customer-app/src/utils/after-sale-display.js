/*
 * 商城售后展示口径的唯一来源：展示分类文案、退款方式与状态文案、预估金额运算。
 *
 * 展示分类（displayStatus）由服务端按「正式退货单状态 + 退款单状态」派生，客户端**不重新推导**，
 * 只做文案与配色映射；level 是语义档位（warn / primary / ok / off / danger），由各页面映射成自己的 BEM 类名。
 */

/** 展示分类文案（与后端 MallAfterSaleDisplayStatusEnum 一一对应） */
const DISPLAY_STATUS_MAP = {
  REVIEWING: { text: '审核中', level: 'warn' },
  REFUNDING: { text: '退款中', level: 'primary' },
  REFUNDED: { text: '退款完成', level: 'ok' },
  REJECTED: { text: '已驳回', level: 'danger' },
  CANCELLED: { text: '已撤销', level: 'off' },
};

/** 退款方式文案（与后端资金来源 method 对应） */
const METHOD_MAP = {
  ONLINE: '在线支付原路退回',
  BALANCE: '退回余额',
};

/** 分摊状态文案（与后端资金分摊状态对应；失败不透出内部失败码） */
const ALLOCATION_STATUS_MAP = {
  PENDING: '待处理',
  PROCESSING: '退款处理中',
  SUCCEEDED: '已退回',
  FAILED: '退款异常，请联系客服',
};

/** 申请售后的快捷原因（仍允许补充说明） */
export const AFTERSALE_REASONS = ['商品损坏/腐烂', '数量短缺', '品质问题', '多送/错送', '其他原因'];

export function afterSaleStatusText(status) {
  return DISPLAY_STATUS_MAP[status]?.text || '处理中';
}

export function afterSaleStatusLevel(status) {
  return DISPLAY_STATUS_MAP[status]?.level || 'off';
}

export function refundMethodText(method) {
  return METHOD_MAP[method] || '原路退回';
}

export function allocationStatusText(status) {
  return ALLOCATION_STATUS_MAP[status] || '处理中';
}

/**
 * 该订单是否出现售后入口：已接单（服务端要求仅已确认订单可申请）即出现，
 * 配送异常归入「已完成」同样可以申请。能不能退、退多少仍由服务端权威判定。
 */
export function canApplyAfterSale(order) {
  return ['PREPARING', 'DELIVERING', 'COMPLETED'].includes(order?.displayStatus);
}

/**
 * 预估退款行金额：申请数量 × 锁定价，按四位小数定点做整数运算（不用浮点）。
 * 仅作申请页展示，核定金额以审核为准。
 */
export function estimateLineAmount(quantity, unitPrice) {
  const qtyScaled = Math.round(Number(quantity) * 10000);
  const priceScaled = Math.round(Number(unitPrice) * 10000);
  if (!Number.isFinite(qtyScaled) || !Number.isFinite(priceScaled) || qtyScaled <= 0 || priceScaled < 0) {
    return null;
  }
  const scaled = Math.round((qtyScaled * priceScaled) / 10000);
  const int = Math.floor(scaled / 10000);
  const frac = String(scaled % 10000).padStart(4, '0');
  return `${int}.${frac}`;
}

/** 预估金额合计：四位小数定点整数相加，不用浮点；跳过空值 */
export function sumAmounts(amounts) {
  const scaled = (amounts || [])
    .filter((amount) => amount !== null && amount !== undefined && amount !== '')
    .reduce((sum, amount) => sum + Math.round(Number(amount) * 10000), 0);
  const int = Math.floor(scaled / 10000);
  const frac = String(scaled % 10000).padStart(4, '0');
  return `${int}.${frac}`;
}

/** 申请数量合法性：大于 0、最多 4 位小数、不超过可申请上限（与服务端 OrderValidator 同口径的客户端预检） */
export function isValidApplyQuantity(quantity, max) {
  if (!/^\d+(\.\d{1,4})?$/.test(String(quantity))) return false;
  const value = Number(quantity);
  return value > 0 && (max === null || max === undefined || value <= Number(max));
}
