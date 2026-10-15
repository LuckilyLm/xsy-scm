/*
 * 将商品接口字段统一转换为页面展示字段，不做定价或库存计算。
 * 没有价格时保留 null，页面显示“询价”，不能显示为 0 元。
 * 其他商品提示直接使用服务端返回值。
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
