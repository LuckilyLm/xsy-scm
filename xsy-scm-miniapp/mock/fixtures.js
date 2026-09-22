/*
 * Mock 契约层 · 假数据
 *
 * ⚠️ 这些字段形状是**暂定契约**，依据：
 *   1. docs/reference/2026-09-22-遗留商城v1契约参考.md（v1 冻结的接口语义）
 *   2. V2 既有领域（product / pricing / customer / inventory 的真实枚举与字段习惯）
 *
 * 后端 `/scm/mall/**` 落地后，**只需核对字段名并调整本文件**，
 * 页面与 api 层不用改（它们只依赖 src/api/mall/** 的签名）。
 *
 * 金额与数量一律用字符串/数字原样透传，不在客户端做求和与格式化以外的运算。
 */

/* ============================ 客户 ============================ */

export const MOCK_CUSTOMER = {
  accountId: 9001,
  customerId: 3001,
  customerCode: 'C03001',
  customerName: '海岸城门店',
  contactName: '张采购',
  phone: '13800001234',
  wechatBound: false,
  customerStatus: 'COOPERATING',
  salesmanName: '李业务',
  salesmanPhone: '13900005678',
};

/* ============================ 分类树 ============================ */

const RAW_TREE = [
  ['蔬菜', ['叶菜类', '根茎类', '瓜果类', '菌菇类']],
  ['水果', ['国产水果', '进口水果', '时令鲜果']],
  ['肉禽蛋', ['猪肉', '牛羊肉', '禽类', '蛋品']],
  ['水产', ['活鲜', '冰鲜', '冻品']],
  ['粮油', ['米面', '食用油', '杂粮']],
  ['调味', ['基础调味', '复合调味', '香辛料']],
];

export const MOCK_CATEGORY_TREE = RAW_TREE.map(([name, children], i) => {
  const categoryId = 100 + i;
  return {
    categoryId,
    categoryName: name,
    parentId: 0,
    level: 1,
    children: children.map((childName, j) => {
      const childId = categoryId * 10 + j + 1;
      return {
        categoryId: childId,
        categoryName: childName,
        parentId: categoryId,
        level: 2,
        children: [],
      };
    }),
  };
});

/** 拍平，便于按 id 反查 */
export const MOCK_CATEGORIES = MOCK_CATEGORY_TREE.flatMap((c) => [c, ...c.children]);

export function findCategoryName(categoryId) {
  const hit = MOCK_CATEGORIES.find((c) => c.categoryId === Number(categoryId));
  return hit ? hit.categoryName : '';
}

/* ============================ 商品 ============================ */
/*
 * 价格来源（与后端 PriceResolver 的优先级一致）：
 *   AGREEMENT      客户协议价
 *   CUSTOMER_TYPE  客户类型价
 *   STANDARD       标准价
 *   UNPRICED       无价格（不可下单）
 *
 * 库存状态：
 *   AVAILABLE / INSUFFICIENT / OUT_OF_STOCK
 *
 * 非标品（isNonStandard）：下单量 ≠ 实际称重量，最终按实重结算。
 */

const PRODUCT_TABLE = [
  // [名称, 规格, 单位, 分类, 是否非标品, 价格, 价格来源, 库存状态, 可售量]
  ['本地小白菜', '500g/份', '份', '叶菜类', false, '3.80', 'AGREEMENT', 'AVAILABLE', 320],
  ['有机菠菜', '400g/把', '把', '叶菜类', false, '6.50', 'AGREEMENT', 'AVAILABLE', 180],
  ['罗马生菜', '300g/颗', '颗', '叶菜类', false, '4.20', 'CUSTOMER_TYPE', 'AVAILABLE', 96],
  ['油麦菜', '500g/份', '份', '叶菜类', false, '3.20', 'CUSTOMER_TYPE', 'INSUFFICIENT', 12],
  ['上海青', '500g/份', '份', '叶菜类', false, '3.50', 'STANDARD', 'AVAILABLE', 240],

  ['荷兰土豆', '净重', '斤', '根茎类', true, '2.80', 'AGREEMENT', 'AVAILABLE', 800],
  ['沙地红萝卜', '净重', '斤', '根茎类', true, '2.40', 'AGREEMENT', 'AVAILABLE', 650],
  ['山东大姜', '净重', '斤', '根茎类', true, '7.60', 'CUSTOMER_TYPE', 'AVAILABLE', 300],
  ['紫皮洋葱', '净重', '斤', '根茎类', true, '2.10', 'STANDARD', 'OUT_OF_STOCK', 0],

  ['本地丝瓜', '净重', '斤', '瓜果类', true, '4.50', 'AGREEMENT', 'AVAILABLE', 210],
  ['水果黄瓜', '500g/盒', '盒', '瓜果类', false, '5.80', 'CUSTOMER_TYPE', 'AVAILABLE', 140],
  ['贝贝南瓜', '净重', '斤', '瓜果类', true, '3.90', 'STANDARD', 'INSUFFICIENT', 8],

  ['鲜香菇', '250g/盒', '盒', '菌菇类', false, '6.80', 'AGREEMENT', 'AVAILABLE', 120],
  ['金针菇', '200g/袋', '袋', '菌菇类', false, '3.60', 'STANDARD', 'AVAILABLE', 260],
  ['白玉菇', '200g/袋', '袋', '菌菇类', false, '4.10', 'STANDARD', 'AVAILABLE', 190],

  ['红富士苹果', '净重', '斤', '国产水果', true, '5.20', 'AGREEMENT', 'AVAILABLE', 420],
  ['库尔勒香梨', '净重', '斤', '国产水果', true, '6.40', 'AGREEMENT', 'AVAILABLE', 260],
  ['海南香蕉', '净重', '斤', '国产水果', true, '3.80', 'CUSTOMER_TYPE', 'AVAILABLE', 380],
  ['赣南脐橙', '净重', '斤', '国产水果', true, '4.60', 'STANDARD', 'AVAILABLE', 300],

  ['进口车厘子', '1kg/盒', '盒', '进口水果', false, '128.00', 'AGREEMENT', 'INSUFFICIENT', 6],
  ['新西兰奇异果', '6个/盒', '盒', '进口水果', false, '39.90', 'CUSTOMER_TYPE', 'AVAILABLE', 45],
  ['泰国金枕榴莲', '净重', '斤', '进口水果', true, '32.00', 'UNPRICED', 'AVAILABLE', 60],

  ['土猪五花肉', '净重', '斤', '猪肉', true, '18.60', 'AGREEMENT', 'AVAILABLE', 150],
  ['猪前腿肉', '净重', '斤', '猪肉', true, '15.80', 'AGREEMENT', 'AVAILABLE', 180],
  ['冷鲜牛腩', '净重', '斤', '牛羊肉', true, '42.00', 'CUSTOMER_TYPE', 'AVAILABLE', 80],
  ['去骨羊腿肉', '净重', '斤', '牛羊肉', true, '48.50', 'STANDARD', 'OUT_OF_STOCK', 0],

  ['白条鸡', '净重', '斤', '禽类', true, '13.20', 'AGREEMENT', 'AVAILABLE', 120],
  ['农家土鸡蛋', '30枚/盒', '盒', '蛋品', false, '26.80', 'AGREEMENT', 'AVAILABLE', 90],

  ['鲜活基围虾', '净重', '斤', '活鲜', true, '58.00', 'CUSTOMER_TYPE', 'AVAILABLE', 40],
  ['冰鲜黄花鱼', '净重', '斤', '冰鲜', true, '22.40', 'STANDARD', 'AVAILABLE', 70],

  ['东北珍珠米', '25kg/袋', '袋', '米面', false, '128.00', 'AGREEMENT', 'AVAILABLE', 55],
  ['一级大豆油', '5L/桶', '桶', '食用油', false, '62.50', 'AGREEMENT', 'AVAILABLE', 130],
  ['海天生抽', '1.9L/瓶', '瓶', '基础调味', false, '18.90', 'STANDARD', 'AVAILABLE', 200],
];

/** 价格来源文案（与后端 PriceResolver 的优先级一致） */
export const PRICE_SOURCE_LABEL = {
  AGREEMENT: '客户协议价',
  CUSTOMER_TYPE: '客户类型价',
  STANDARD: '标准价',
  UNPRICED: '暂无报价',
};

export const MOCK_PRODUCTS = PRODUCT_TABLE.map((row, i) => {
  const [productName, spec, unit, categoryName, isNonStandard, price, priceSource, stockStatus, availableQty] = row;
  const category = MOCK_CATEGORIES.find((c) => c.categoryName === categoryName);
  const skuId = 5000 + i;

  return {
    skuId,
    spuId: 4000 + i,
    productName,
    skuName: spec,
    spec,
    unit,
    categoryId: category ? category.categoryId : null,
    categoryName,
    // 商城图片待业主提供后替换为真实 fileKey 派生的 URL
    imageUrl: '',
    isNonStandard,
    price,
    priceSource,
    priceSourceLabel: PRICE_SOURCE_LABEL[priceSource],
    stockStatus,
    availableQty,
    minOrderQty: isNonStandard ? 1 : 1,
    stepQty: 1,
    // 常购标记：接常购接口后由后端返回
    favorite: i % 7 === 0,
  };
});

export function findProduct(skuId) {
  return MOCK_PRODUCTS.find((p) => p.skuId === Number(skuId)) || null;
}

/* ============================ 首页装修 ============================ */

export const MOCK_HOME_FLOORS = [
  { type: 'NOTICE', content: '中秋备货高峰，请提前 1 天下单，配送时效以实际为准。' },
  {
    type: 'CATEGORY_NAV',
    items: MOCK_CATEGORY_TREE.map((c) => ({
      categoryId: c.categoryId,
      categoryName: c.categoryName,
    })),
  },
  { type: 'REORDER', title: '再来一单', orderId: 70001, orderNo: 'SO20260918001' },
  {
    type: 'FAVORITE',
    title: '常购商品',
    items: MOCK_PRODUCTS.filter((p) => p.favorite).slice(0, 6),
  },
  {
    type: 'NEW_ARRIVAL',
    title: '新品推荐',
    items: MOCK_PRODUCTS.slice(20, 26),
  },
  {
    type: 'RECOMMEND',
    title: '推荐商品',
    items: MOCK_PRODUCTS.slice(0, 10),
  },
];
