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

import { categoryEmoji } from './category-icon';

/** 库存状态枚举（服务端给出，与后端一致） */
export const STOCK_STATUS = {
  AVAILABLE: 'AVAILABLE',
  INSUFFICIENT: 'INSUFFICIENT',
  OUT_OF_STOCK: 'OUT_OF_STOCK',
  /** 商城尚未确定可售库存口径时服务端显式返回，页面如实显示「库存未知」 */
  UNKNOWN: 'UNKNOWN',
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
  [STOCK_STATUS.UNKNOWN]: { text: '库存未知', level: 'off' },
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

/** 是否有客户价。null / undefined / '' / 0 都视为无价，避免把 ¥0.00 当报价展示 */
export function hasPrice(product) {
  if (!product || product.price === null || product.price === undefined || product.price === '') return false;
  const value = Number(product.price);
  return Number.isFinite(value) && value > 0;
}

/**
 * 是否可下单：有客户价且服务端可售。
 * 本商城「先接单后采购」，库存为 0 只显示「预订」，不阻止下单；
 * 真正的可售、报价与数量校验在结算和提交时由服务端重新执行。
 */
export function isOrderable(product) {
  // 可售与否以服务端 sellable 为准：本商城「先接单后采购」，库存为 0 仍可下单。
  return hasPrice(product) && product.sellable !== false;
}

/** 操作按钮文案：询价 / 加购 / 预订 / 不可售 */
export function actionText(product) {
  if (!hasPrice(product)) {
    return '询价';
  }
  if (!isOrderable(product)) {
    return '不可售';
  }
  // 先接单后采购：库存为 0 仍可下单，按钮语义用「预订」而不是「加购」
  if (product.stockStatus === STOCK_STATUS.OUT_OF_STOCK) {
    return '预订';
  }
  return '加购';
}

/** 无图时的占位首字（业主提供 fileKey 前的兜底展示） */
export function thumbText(product) {
  // 无图占位优先用分类 emoji：单字「一」「南」放大后辨识度低，emoji 更像商品图
  if (product && product.categoryName) {
    return categoryEmoji(product.categoryName);
  }
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

/**
 * 价格来源文案：服务端给 label 优先，其次按枚举翻译。
 *
 * 认不出的枚举一律不显示标签 —— 宁可少一个角标，也不要把 STANDARD 这类
 * 英文常量端给客户；新增来源时由 price-source-contract 契约测试拦下。
 */
const PRICE_SOURCE_LABELS = {
  MARKET: '市场价',
  AGREEMENT: '协议价',
  CUSTOMER_TYPE: '类型价',
  TIME_PRICE: '时价',
};

export function priceSourceLabel(product) {
  if (!product) {
    return '';
  }
  // 后端只给枚举值，缺省时按枚举翻译，避免把 MARKET 之类的英文直出给用户。
  return product.priceSourceLabel || PRICE_SOURCE_LABELS[product.priceSource] || '';
}

/**
 * 价格来源语义档位：agreement / type / time / standard / unpriced。
 *
 * 与 stockLevel 同一套路：这里只给语义名，各组件自己映射成 BEM 类名与配色，
 * 避免把色值或类名写进展示口径层。时价是短期价格，单独成档以免和市场价混色。
 */
export function priceSourceLevel(product) {
  const source = product && product.priceSource;
  if (source === 'AGREEMENT') {
    return 'agreement';
  }
  if (source === 'CUSTOMER_TYPE') {
    return 'type';
  }
  if (source === 'TIME_PRICE') {
    return 'time';
  }
  if (source === 'UNPRICED') {
    return 'unpriced';
  }
  return 'standard';
}

/** 规格 / 分类合并展示，如「500g/份 · 叶菜类」 */
export function specCategoryText(product) {
  if (!product) {
    return '';
  }
  return [product.spec, product.categoryName].filter(Boolean).join(' · ');
}
