/*
 * 商城商品可见性、客户价格和库存由服务端按登录客户决定。
 * 页面只使用 product-adapter 转换后的字段；无价格时显示“询价”。
 */
import { getRequest } from '@/lib/smart-request';
import { adaptPage, adaptProduct, adaptProducts } from './product-adapter';

/** 映射 ResponseDTO.data，其余字段原样保留 */
async function withData(promise, map) {
  const response = await promise;
  return { ...response, data: map(response.data) };
}

/** 分类树（只返回有可见 SKU 的分类） */
/** 服务端分类节点用 name，页面统一读 categoryName（与商品卡片字段一致） */
function adaptCategory(node) {
  return {
    ...node,
    categoryName: node.name,
    children: (node.children || []).map(adaptCategory),
  };
}

export const mallCatalogApi = {
  /** 分类树（只返回有可见 SKU 的分类） */
  getCategories: () => withData(getRequest('/scm/mall/catalog/categories'), (list) => (list || []).map(adaptCategory)),

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
   * 常购商品返回数组，不是分页列表；查询仅传 days 和 limit。
   * @param {{days?: number, limit?: number}} param
   */
  getFavorites: (param) => withData(getRequest('/scm/mall/catalog/frequent', param), adaptProducts),

  /** 搜索热词 / 历史（历史词由客户端本地维护，热词来自服务端） */
  getHotKeywords: () => getRequest('/scm/mall/catalog/hot-keywords'),
};
