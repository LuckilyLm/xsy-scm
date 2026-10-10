/*
 * 商城商品目录接口（分类 / 搜索 / 商品详情 / 常购）
 *
 * 商品可见性、客户价格和库存展示均由服务端按当前商城身份返回。
 *
 * 关键约束：
 * - 商品可见性与客户价一律服务端过滤/计算，客户端不做二次过滤与改价；
 * - 后端返回领域字段（unitPrice / saleUnit / specName / primaryImageUrl），
 *   统一经 product-adapter 映射为展示模型，页面不直接读后端字段名；
 * - unitPrice 为 null 时展示「询价」，禁止显示 ¥0.00；
 * - 库存展示口径（具体数字 / 有货缺货）待产品裁决，客户端只展示服务端返回值。
 */
import { getRequest } from '@/lib/smart-request';
import { adaptPage, adaptProduct, adaptProducts } from './product-adapter';

/** 映射 ResponseDTO.data，其余字段原样保留 */
async function withData(promise, map) {
  const response = await promise;
  return { ...response, data: map(response.data) };
}

export const mallCatalogApi = {
  /** 分类树（只返回有可见 SKU 的分类） */
  getCategories: () => getRequest('/scm/mall/catalog/categories'),

  /**
   * 商品分页
   * @param {{pageNum: number, pageSize: number, keyword?: string, categoryId?: number}} param
   */
  getProducts: (param) => withData(getRequest('/scm/mall/catalog/products', param), adaptPage),

  /**
   * 商品详情
   * @param {number} skuId
   */
  getProduct: (skuId) => withData(getRequest(`/scm/mall/catalog/products/${skuId}`), adaptProduct),

  /**
   * 常购商品（规划文档 §14：优先于「猜你喜欢」）
   *
   * 后端契约：`GET /scm/mall/catalog/frequent` 返回 **MallProductVO 数组**（不是分页包装），
   * 查询参数为 days（统计窗口天数，1–365）与 limit（返回条数，1–100）。
   * 调用方直接读 `response.data`，不要读 `data.list`，也不要传 pageNum/pageSize。
   *
   * @param {{days?: number, limit?: number}} param
   */
  getFavorites: (param) => withData(getRequest('/scm/mall/catalog/frequent', param), adaptProducts),

  /** 搜索热词 / 历史（历史词由客户端本地维护，热词来自服务端） */
  getHotKeywords: () => getRequest('/scm/mall/catalog/hot-keywords'),
};
