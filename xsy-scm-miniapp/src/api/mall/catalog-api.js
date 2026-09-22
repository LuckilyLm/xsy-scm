/*
 * 商城商品目录接口（分类 / 搜索 / 商品详情）
 *
 * ⚠️ 后端尚未实现，路径与字段以后端冻结契约为准。
 *
 * 关键约束（规划文档 §11 / §13）：
 * - 商品可见性与客户价一律服务端过滤/计算，客户端不做二次过滤与改价；
 * - unitPrice 为 null 时展示「询价」，禁止显示 ¥0.00；
 * - 库存展示口径（具体数字 / 有货缺货）待产品裁决，客户端只展示服务端返回值。
 */
import { getRequest } from '@/lib/smart-request';

export const mallCatalogApi = {
  /** 分类树（只返回有可见 SKU 的分类） */
  getCategories: () => getRequest('/scm/mall/catalog/categories'),

  /**
   * 商品分页
   * @param {{pageNum: number, pageSize: number, keyword?: string, categoryId?: number}} param
   */
  getProducts: (param) => getRequest('/scm/mall/catalog/products', param),

  /**
   * 商品详情
   * @param {number} skuId
   */
  getProduct: (skuId) => getRequest(`/scm/mall/catalog/products/${skuId}`),

  /**
   * 常购商品（规划文档 §14：优先于「猜你喜欢」）
   * @param {{pageNum: number, pageSize: number}} param
   */
  getFavorites: (param) => getRequest('/scm/mall/catalog/favorites', param),

  /** 搜索热词 / 历史（历史词由客户端本地维护，热词来自服务端） */
  getHotKeywords: () => getRequest('/scm/mall/catalog/hot-keywords'),
};
