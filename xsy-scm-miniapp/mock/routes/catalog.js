/*
 * Mock 契约层 · 商品目录（分类 / 商品列表 / 详情 / 常购 / 热词）
 *
 * 对齐 src/api/mall/catalog-api.js 的路径。
 *
 * 关键约束（规划 §11 / §13）：
 * - 可见性与客户价由"服务端"过滤/计算 —— mock 侧同样只返回已定价、可见的 SKU，
 *   客户端不得再过滤或改价；
 * - 价格为 null 时页面展示「询价」，不显示 ¥0.00；
 * - 分页参数是 pageNum / pageSize。
 */
import { currentCustomer, fail, ok, paginate } from '../helpers';
import { MOCK_CATEGORIES, MOCK_CATEGORY_TREE, MOCK_PRODUCTS } from '../fixtures';
import { PLATFORM_ERROR_CODE } from '@/constants/error-code-const';

/** 取某个分类及其所有后代分类的 id，用于「选一级分类看全部子类商品」 */
function categoryIdWithDescendants(categoryId) {
  const id = Number(categoryId);
  const hit = MOCK_CATEGORIES.find((c) => c.categoryId === id);
  if (!hit) {
    return [];
  }
  if (hit.level === 1) {
    return [id, ...hit.children.map((c) => c.categoryId)];
  }
  return [id];
}

const HOT_KEYWORDS = ['小白菜', '土豆', '鸡蛋', '五花肉', '苹果', '基围虾', '大米', '食用油'];

export const catalogRoutes = [
  {
    method: 'GET',
    path: '/scm/mall/catalog/categories',
    handler: async () => ok(MOCK_CATEGORY_TREE),
  },

  {
    method: 'GET',
    path: '/scm/mall/catalog/products',
    handler: async ({ data }) => {
      const { categoryId, keyword, pageNum, pageSize } = data || {};
      let list = MOCK_PRODUCTS;

      if (categoryId) {
        const ids = categoryIdWithDescendants(categoryId);
        list = list.filter((p) => ids.includes(p.categoryId));
      }

      const kw = String(keyword || '').trim();
      if (kw) {
        const lower = kw.toLowerCase();
        list = list.filter(
          (p) =>
            p.productName.toLowerCase().includes(lower) || p.skuName.toLowerCase().includes(lower) || p.categoryName.toLowerCase().includes(lower)
        );
      }

      return ok(paginate(list, { pageNum, pageSize }));
    },
  },

  {
    method: 'GET',
    path: '/scm/mall/catalog/products/:skuId',
    handler: async ({ params }) => {
      const product = MOCK_PRODUCTS.find((p) => p.skuId === Number(params.skuId));
      if (!product) {
        return fail(PLATFORM_ERROR_CODE.DATA_NOT_EXIST, '商品不存在或已下架');
      }
      // 详情比列表多出配送说明等字段
      return ok({
        ...product,
        deliveryTip: '当日 20:00 前下单，次日 06:00–10:00 配送',
        nonStandardTip: product.isNonStandard ? '本商品按实际称重结算，下单量为预估量，最终金额以出库实重为准。' : '',
        description: `${product.productName}（${product.spec}），产地直采，冷链配送。`,
      });
    },
  },

  {
    method: 'GET',
    path: '/scm/mall/catalog/favorites',
    handler: async ({ data }) => {
      const session = currentCustomer();
      if (!session) {
        return fail(PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID, '登录已失效，请重新登录');
      }
      return ok(
        paginate(
          MOCK_PRODUCTS.filter((p) => p.favorite),
          data || {}
        )
      );
    },
  },

  {
    method: 'GET',
    path: '/scm/mall/catalog/hot-keywords',
    handler: async () => ok(HOT_KEYWORDS),
  },
];
