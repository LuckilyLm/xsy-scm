/*
 * 商品展示口径（列表卡片 / 详情页共用）
 *
 * 这里只做**展示映射**，不做任何业务判断或金额运算（规划 §18 同源原则 / §38.6）：
 * - 库存状态由服务端枚举映射为文案，客户端不自行推断「够不够」；
 * - 无价格一律展示「询价」，**绝不显示 ¥0.00**（规划 §11.3）；
 * - 非标品只做标注，最终金额由服务端在结算 preview 时给出（规划 §17.1）。
 *
 * 之所以抽出来：同一规则若在 ProductCard 与详情页各写一遍，
 * 迟早出现「卡片显示有货、详情显示缺货」这类不一致。
 */

/** 库存状态枚举（服务端给出，与后端一致） */
export const STOCK_STATUS = {
  AVAILABLE: 'AVAILABLE',
  INSUFFICIENT: 'INSUFFICIENT',
  OUT_OF_STOCK: 'OUT_OF_STOCK',
};

/**
 * 库存文案。
 * level 是语义档位（ok / warn / off），由各组件映射成自己的 BEM 类名，
 * 这样组件不共享类名，样式仍各自独立。
 */
const STOCK_MAP = {
  [STOCK_STATUS.AVAILABLE]: { text: '有货', level: 'ok' },
  [STOCK_STATUS.INSUFFICIENT]: { text: '库存不足', level: 'warn' },
  [STOCK_STATUS.OUT_OF_STOCK]: { text: '暂时缺货', level: 'off' },
};

const UNKNOWN_STOCK = { text: '库存未知', level: 'off' };

function stockEntry(product) {
  return (product && STOCK_MAP[product.stockStatus]) || UNKNOWN_STOCK;
}

/** 库存文案，如「有货」「库存不足」「暂时缺货」 */
export function stockText(product) {
  return stockEntry(product).text;
}

/** 库存语义档位：ok / warn / off */
export function stockLevel(product) {
  return stockEntry(product).level;
}

/** 是否展示具体可售量：只有「有货」时展示，避免把不足/缺货的数字放大 */
export function showAvailableQty(product) {
  return !!product && product.stockStatus === STOCK_STATUS.AVAILABLE && product.availableQty != null;
}

/** 是否有客户价。null / undefined / '' 都视为无价 */
export function hasPrice(product) {
  return !!product && product.price !== null && product.price !== undefined && product.price !== '';
}

/**
 * 是否可下单。
 * 无价（需询价）或已缺货时置灰，避免用户反复点到必然失败的动作。
 * 注意：这只控制按钮可用性，真正的库存与价格校验仍在服务端事务内完成（规划 §38.7）。
 */
export function isOrderable(product) {
  return hasPrice(product) && product.stockStatus !== STOCK_STATUS.OUT_OF_STOCK;
}

/** 操作按钮文案：询价 / 缺货 / 加购 */
export function actionText(product) {
  if (!hasPrice(product)) {
    return '询价';
  }
  if (product && product.stockStatus === STOCK_STATUS.OUT_OF_STOCK) {
    return '缺货';
  }
  return '加购';
}

/** 无图时的占位首字（业主提供 fileKey 前的兜底展示） */
export function thumbText(product) {
  return ((product && product.productName) || '商').slice(0, 1);
}

/** 是否非标品（按实重结算） */
export function isNonStandard(product) {
  return !!(product && product.isNonStandard);
}

/**
 * 非标品说明文案。
 * 优先用服务端下发的 nonStandardTip，服务端没给才用本地兜底，
 * 保证「下单量 ≠ 最终实重」这句话在任何情况下都出现（规划 §11.2）。
 */
export function nonStandardTip(product) {
  if (product && product.nonStandardTip) {
    return product.nonStandardTip;
  }
  return '本商品按实际称重结算，下单量为预估量，最终金额以出库实重为准。';
}

/** 价格来源文案：服务端给 label 优先，退化到原始枚举，最后兜底 */
export function priceSourceLabel(product) {
  if (!product) {
    return '';
  }
  return product.priceSourceLabel || product.priceSource || '';
}
