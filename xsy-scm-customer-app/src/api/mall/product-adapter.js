/*
 * 后端商品契约（MallProductVO）→ 前端展示模型的唯一映射点。
 *
 * 后端 /scm/mall/catalog/** 返回领域字段（unitPrice / saleUnit / specName / primaryImageUrl …），
 * 而商品卡片与详情页沿用展示字段（price / unit / spec / imageUrl …）。两边字段名不同，
 * 必须在 API 边界一次性对齐；否则接真实接口后价格、规格与图片都会是空。
 *
 * 约束：
 * - 只做字段改名与派生，不做任何业务判断或金额运算；
 * - 无价一律映射为 price = null，由展示层渲染「询价」，绝不显示 ¥0.00；
 * - 展示层需要的额外字段（deliveryTip / nonStandardTip 等）由后端在 VO 上原样透传。
 */

/** 价格来源文案，与后端 ScmPriceSourceEnum 对应 */
const PRICE_SOURCE_LABEL = {
  AGREEMENT: '客户协议价',
  CUSTOMER_TYPE: '客户类型价',
  STANDARD: '标准价',
};

/** 单个商品：后端 VO → 展示模型 */
export function adaptProduct(raw) {
  if (!raw) return raw;
  const priced = raw.unitPrice !== null && raw.unitPrice !== undefined && raw.unitPrice !== '';
  return {
    skuId: raw.skuId,
    spuId: raw.spuId,
    productName: raw.productName || '',
    skuName: raw.specName || '',
    spec: raw.specName || '',
    unit: raw.saleUnit || '',
    categoryId: raw.categoryId,
    categoryName: raw.categoryName || '',
    imageUrl: raw.primaryImageUrl || '',
    isNonStandard: raw.nonStandard === true || raw.productType === 'NON_STANDARD',
    price: priced ? raw.unitPrice : null,
    priceSource: priced ? raw.priceSource : 'UNPRICED',
    priceSourceLabel: priced ? PRICE_SOURCE_LABEL[raw.priceSource] || '' : '',
    stockStatus: raw.stockStatus || '',
    availableQty: raw.availableQty ?? null,
    description: raw.description || '',
    deliveryTip: raw.deliveryTip || '',
    nonStandardTip: raw.nonStandardTip || '',
  };
}

/** 商品数组映射；非数组入参返回空数组 */
export function adaptProducts(list) {
  return Array.isArray(list) ? list.map(adaptProduct) : [];
}

/** 分页结果映射：保留 pageNum/pageSize/total/pages，仅映射 list */
export function adaptPage(page) {
  if (!page || typeof page !== 'object') return page;
  return { ...page, list: adaptProducts(page.list) };
}
