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

/*
 * 商品目录（mock 契约层）
 *
 * 商品名与图片**全部对齐业主提供的真实 SKU**（82 张白底图，已裁剪为 240×240 JPEG）。
 * 素材源目录：D:\Browser Download\xsy-data\测试图片\{蔬菜,水果,肉禽蛋类,水产,粮油,调味}
 *
 * 范围（业主 2026-09-25 确认）：
 *   - 商品目录用真实 SKU，不用通用名
 *   - **六个一级分类全部有 SKU**（第二轮补充了肉禽蛋 / 水产 / 粮油 / 调味素材）
 *
 * 两处「目录与素材分类不一致」的处理（按真实业务域归类，不跟目录走）：
 *   - 牛油果：业主放在 蔬菜/ 目录，但商品归到 水果 / 进口水果
 *   - 酱油：  业主放在 粮油/ 目录，但商品归到 调味 / 基础调味
 *
 * 另外刻意留了 2 个「图片待补」的 SKU（山东大姜 / 白玉菇），
 * 让 ProductCard 的**无图 fallback 在页面上持续可见**，不必只靠测试注入验证。
 */
const PRODUCT_TABLE = [
  // [名称, 规格, 单位, 二级分类, 是否非标品, 价格, 价格来源, 库存状态, 可售量, 图片键]
  /* ---- 蔬菜 / 叶菜类 ---- */
  ['上海青', '500g/份', '份', '叶菜类', false, '3.50', 'STANDARD', 'AVAILABLE', 240, 'shanghaiqing'],
  ['生菜', '300g/颗', '颗', '叶菜类', false, '4.20', 'CUSTOMER_TYPE', 'AVAILABLE', 96, 'shenglai'],
  ['菜心', '500g/份', '份', '叶菜类', false, '3.80', 'AGREEMENT', 'AVAILABLE', 320, 'caixin'],
  ['空心菜', '500g/份', '份', '叶菜类', false, '3.20', 'CUSTOMER_TYPE', 'INSUFFICIENT', 12, 'kongxincai'],
  ['西兰花', '400g/颗', '颗', '叶菜类', false, '5.60', 'AGREEMENT', 'AVAILABLE', 150, 'xilanhua'],

  /* ---- 蔬菜 / 根茎类 ---- */
  ['土豆', '净重', '斤', '根茎类', true, '2.80', 'AGREEMENT', 'AVAILABLE', 800, 'tudou'],
  ['白萝卜', '净重', '斤', '根茎类', true, '2.40', 'AGREEMENT', 'AVAILABLE', 650, 'bailuobo'],
  ['江西信丰萝卜', '净重', '斤', '根茎类', true, '3.10', 'CUSTOMER_TYPE', 'AVAILABLE', 300, 'xinfengluobo'],
  ['洋葱', '净重', '斤', '根茎类', true, '2.10', 'STANDARD', 'OUT_OF_STOCK', 0, 'yangcong'],
  ['大蒜', '净重', '斤', '根茎类', true, '7.60', 'CUSTOMER_TYPE', 'AVAILABLE', 300, 'dasuan'],
  ['莲藕', '净重', '斤', '根茎类', true, '6.20', 'AGREEMENT', 'AVAILABLE', 180, 'lianou'],
  // 图片待补：刻意留空以持续验证无图 fallback
  ['山东大姜', '净重', '斤', '根茎类', true, '7.20', 'CUSTOMER_TYPE', 'AVAILABLE', 260, ''],

  /* ---- 蔬菜 / 瓜果类 ---- */
  ['黄瓜', '500g/盒', '盒', '瓜果类', false, '5.80', 'CUSTOMER_TYPE', 'AVAILABLE', 140, 'huanggua'],
  ['南瓜', '净重', '斤', '瓜果类', true, '3.90', 'STANDARD', 'INSUFFICIENT', 8, 'nangua'],
  ['西红柿', '净重', '斤', '瓜果类', true, '4.50', 'AGREEMENT', 'AVAILABLE', 210, 'xihongshi'],
  ['甜玉米', '2根/份', '份', '瓜果类', false, '4.80', 'AGREEMENT', 'AVAILABLE', 260, 'tianyumi'],
  ['红线椒', '净重', '斤', '瓜果类', true, '8.90', 'STANDARD', 'AVAILABLE', 90, 'hongxianjiao'],

  /* ---- 蔬菜 / 菌菇类 ---- */
  ['香菇', '250g/盒', '盒', '菌菇类', false, '6.80', 'AGREEMENT', 'AVAILABLE', 120, 'xianggu'],
  ['金针菇', '200g/袋', '袋', '菌菇类', false, '3.60', 'STANDARD', 'AVAILABLE', 260, 'jinzhengu'],
  // 图片待补：同山东大姜
  ['白玉菇', '200g/袋', '袋', '菌菇类', false, '4.10', 'STANDARD', 'AVAILABLE', 190, ''],

  /* ---- 水果 / 国产水果 ---- */
  ['苹果', '净重', '斤', '国产水果', true, '5.20', 'AGREEMENT', 'AVAILABLE', 420, 'pingguo'],
  ['橙子', '净重', '斤', '国产水果', true, '4.60', 'STANDARD', 'AVAILABLE', 300, 'chengzi'],
  ['香蕉', '净重', '斤', '国产水果', true, '3.80', 'CUSTOMER_TYPE', 'AVAILABLE', 380, 'xiangjiao'],
  ['荔枝', '净重', '斤', '国产水果', true, '12.80', 'AGREEMENT', 'AVAILABLE', 120, 'lizhi'],
  ['妃子笑', '净重', '斤', '国产水果', true, '18.60', 'AGREEMENT', 'INSUFFICIENT', 6, 'feizixiao'],
  ['桂味', '净重', '斤', '国产水果', true, '22.00', 'CUSTOMER_TYPE', 'AVAILABLE', 45, 'guiwei'],
  ['麒麟瓜', '净重', '斤', '国产水果', true, '3.40', 'STANDARD', 'AVAILABLE', 500, 'qilingua'],
  ['甘美4K', '净重', '斤', '国产水果', true, '4.20', 'AGREEMENT', 'AVAILABLE', 260, 'ganmei4k'],

  /* ---- 水果 / 进口水果 ---- */
  ['凤梨', '1.2kg/个', '个', '进口水果', false, '16.80', 'CUSTOMER_TYPE', 'AVAILABLE', 80, 'fengli'],
  ['牛油果', '6个/盒', '盒', '进口水果', false, '29.90', 'AGREEMENT', 'AVAILABLE', 60, 'niuyouguo'],
  ['黑美人西瓜', '净重', '斤', '进口水果', true, '3.60', 'STANDARD', 'AVAILABLE', 320, 'heimeirenxigua'],
  // 无客户价：用于验证「询价」状态（规划 §11.3 绝不显示 ¥0.00）
  // 无客户价样本：price 必须为 null —— price 是「是否存在客户价」的单一事实，
  // priceSource 只表达来源语义，不能变成第二套价格真相（否则会被渲染成 ¥9.80 + 加购）
  ['红心火龙果', '净重', '斤', '进口水果', true, null, 'UNPRICED', 'AVAILABLE', 70, 'hongxinhuolongguo'],
  ['紫葡萄', '净重', '斤', '进口水果', true, '14.50', 'AGREEMENT', 'AVAILABLE', 110, 'ziputao'],

  /* ---- 水果 / 时令鲜果 ---- */
  ['提子', '净重', '斤', '时令鲜果', true, '12.60', 'CUSTOMER_TYPE', 'AVAILABLE', 150, 'tizi'],
  ['黑提', '净重', '斤', '时令鲜果', true, '15.80', 'AGREEMENT', 'AVAILABLE', 95, 'heiti'],
  ['板栗', '净重', '斤', '时令鲜果', true, '11.20', 'AGREEMENT', 'AVAILABLE', 200, 'banli'],
  // 三位数价格样本：¥128.00 /盒，用于验证窄屏下价格不被挤压
  ['迁西板栗', '5斤礼盒', '盒', '时令鲜果', false, '128.00', 'AGREEMENT', 'AVAILABLE', 45, 'qianxibanli'],
  ['遵化板栗', '净重', '斤', '时令鲜果', true, '13.60', 'STANDARD', 'AVAILABLE', 160, 'zunhuabanli'],

  /* ==================== 肉禽蛋 ==================== */
  ['猪肉', '净重', '斤', '猪肉', true, '18.60', 'AGREEMENT', 'AVAILABLE', 150, 'zhurou'],
  ['排骨', '净重', '斤', '猪肉', true, '22.80', 'AGREEMENT', 'AVAILABLE', 120, 'paigu'],
  ['牛腩', '净重', '斤', '牛羊肉', true, '42.00', 'CUSTOMER_TYPE', 'AVAILABLE', 80, 'niunan'],
  ['牛肉', '净重', '斤', '牛羊肉', true, '48.00', 'AGREEMENT', 'AVAILABLE', 90, 'niurou'],
  ['羊排', '净重', '斤', '牛羊肉', true, '52.00', 'STANDARD', 'INSUFFICIENT', 6, 'yangpai'],
  ['鸡腿', '净重', '斤', '禽类', true, '13.20', 'AGREEMENT', 'AVAILABLE', 160, 'jitui'],
  ['鸡翅', '净重', '斤', '禽类', true, '15.60', 'CUSTOMER_TYPE', 'AVAILABLE', 140, 'jichi'],
  ['鸭胸', '净重', '斤', '禽类', true, '19.80', 'AGREEMENT', 'AVAILABLE', 70, 'yaxiong'],
  ['鸡蛋', '30枚/盒', '盒', '蛋品', false, '26.80', 'AGREEMENT', 'AVAILABLE', 200, 'jidan'],
  ['鸭蛋', '20枚/盒', '盒', '蛋品', false, '22.50', 'STANDARD', 'AVAILABLE', 120, 'yadan'],
  ['鹌鹑蛋', '30枚/盒', '盒', '蛋品', false, '12.60', 'CUSTOMER_TYPE', 'AVAILABLE', 180, 'anchundan'],

  /* ==================== 水产 ==================== */
  ['基围虾', '净重', '斤', '活鲜', true, '58.00', 'CUSTOMER_TYPE', 'AVAILABLE', 40, 'jiweixia'],
  ['梭子蟹', '净重', '斤', '活鲜', true, '68.00', 'AGREEMENT', 'AVAILABLE', 35, 'suozixie'],
  ['花蛤', '净重', '斤', '活鲜', true, '12.80', 'STANDARD', 'AVAILABLE', 90, 'huage'],
  ['生蚝', '净重', '斤', '活鲜', true, '26.00', 'AGREEMENT', 'INSUFFICIENT', 8, 'shenghao'],
  ['青口贝', '净重', '斤', '活鲜', true, '18.50', 'CUSTOMER_TYPE', 'AVAILABLE', 60, 'qingkoubei'],
  ['扇贝', '净重', '斤', '活鲜', true, '32.00', 'AGREEMENT', 'AVAILABLE', 50, 'shanbei'],
  ['鲈鱼', '净重', '斤', '活鲜', true, '24.00', 'STANDARD', 'AVAILABLE', 45, 'luyu'],
  ['三文鱼', '净重', '斤', '冰鲜', true, '88.00', 'AGREEMENT', 'AVAILABLE', 30, 'sanwenyu'],
  ['鱿鱼', '净重', '斤', '冰鲜', true, '28.00', 'CUSTOMER_TYPE', 'AVAILABLE', 55, 'youyu'],
  ['章鱼', '净重', '斤', '冰鲜', true, '46.00', 'STANDARD', 'OUT_OF_STOCK', 0, 'zhangyu'],

  /* ==================== 粮油 ==================== */
  ['大米', '25kg/袋', '袋', '米面', false, '128.00', 'AGREEMENT', 'AVAILABLE', 55, 'dami'],
  ['面粉', '25kg/袋', '袋', '米面', false, '96.00', 'AGREEMENT', 'AVAILABLE', 60, 'mianfen'],
  ['玉米碴', '2.5kg/袋', '袋', '米面', false, '18.60', 'STANDARD', 'AVAILABLE', 100, 'yumicha'],
  ['燕麦片', '1kg/袋', '袋', '米面', false, '22.80', 'CUSTOMER_TYPE', 'AVAILABLE', 80, 'yanmaipian'],
  ['玉米面', '2.5kg/袋', '袋', '米面', false, '16.80', 'STANDARD', 'AVAILABLE', 90, 'yumimian'],
  // 黑米业主给了散装 / 袋装两张图，按不同包装拆成两个 SKU
  ['黑米（散装）', '净重', '斤', '米面', true, '6.80', 'AGREEMENT', 'AVAILABLE', 200, 'heimi'],
  ['黑米（袋装）', '2.5kg/袋', '袋', '米面', false, '32.00', 'AGREEMENT', 'AVAILABLE', 70, 'heimidazhuang'],
  ['薏米', '1kg/袋', '袋', '米面', false, '24.60', 'CUSTOMER_TYPE', 'AVAILABLE', 85, 'yimi'],
  ['荞麦米', '1kg/袋', '袋', '米面', false, '19.80', 'STANDARD', 'AVAILABLE', 95, 'qiaomaimi'],
  ['橄榄油', '1L/瓶', '瓶', '食用油', false, '78.00', 'AGREEMENT', 'AVAILABLE', 60, 'ganlanyou'],
  ['香油', '500ml/瓶', '瓶', '食用油', false, '26.80', 'CUSTOMER_TYPE', 'AVAILABLE', 110, 'xiangyou'],
  ['花生油', '5L/桶', '桶', '食用油', false, '118.00', 'AGREEMENT', 'AVAILABLE', 45, 'huashengyou'],
  ['葵花籽油', '5L/桶', '桶', '食用油', false, '86.00', 'STANDARD', 'AVAILABLE', 50, 'kuihuaziyou'],
  ['玉米油', '5L/桶', '桶', '食用油', false, '92.00', 'AGREEMENT', 'AVAILABLE', 48, 'yumiyou'],

  /* ==================== 调味 ==================== */
  // 酱油业主放在「粮油」目录，商品分类按真实域归到「基础调味」
  ['酱油', '1.9L/瓶', '瓶', '基础调味', false, '18.90', 'STANDARD', 'AVAILABLE', 200, 'jiangyou'],
  ['生抽', '1.9L/瓶', '瓶', '基础调味', false, '21.60', 'AGREEMENT', 'AVAILABLE', 180, 'shengchou'],
  ['香醋', '500ml/瓶', '瓶', '基础调味', false, '9.80', 'CUSTOMER_TYPE', 'AVAILABLE', 220, 'xiangcu'],
  ['盐', '1kg/袋', '袋', '基础调味', false, '3.60', 'STANDARD', 'AVAILABLE', 500, 'yan'],
  ['白砂糖', '1kg/袋', '袋', '基础调味', false, '8.20', 'AGREEMENT', 'AVAILABLE', 300, 'baishatang'],
  ['冰糖', '1kg/袋', '袋', '基础调味', false, '12.60', 'CUSTOMER_TYPE', 'AVAILABLE', 200, 'bingtang'],
  ['芝麻酱', '500g/瓶', '瓶', '复合调味', false, '16.80', 'AGREEMENT', 'AVAILABLE', 130, 'zhimajiang'],
  ['花椒', '500g/袋', '袋', '香辛料', false, '32.00', 'CUSTOMER_TYPE', 'AVAILABLE', 90, 'huajiao'],
  ['孜然', '500g/袋', '袋', '香辛料', false, '28.50', 'STANDARD', 'AVAILABLE', 100, 'ziran'],
  ['黑胡椒', '500g/袋', '袋', '香辛料', false, '36.00', 'AGREEMENT', 'AVAILABLE', 80, 'heihujiao'],
  ['干辣椒八角', '500g/袋', '袋', '香辛料', false, '24.00', 'STANDARD', 'AVAILABLE', 120, 'ganlajiao'],
];

/** 价格来源文案（与后端 PriceResolver 的优先级一致） */
export const PRICE_SOURCE_LABEL = {
  AGREEMENT: '客户协议价',
  CUSTOMER_TYPE: '客户类型价',
  STANDARD: '标准价',
  UNPRICED: '暂无报价',
};

export const MOCK_PRODUCTS = PRODUCT_TABLE.map((row, i) => {
  const [productName, spec, unit, categoryName, isNonStandard, price, priceSource, stockStatus, availableQty, imageKey] = row;
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
    // 商城图片待业主提供后替换为真实 fileKey 派生的 URL；
    // 这里先用业主提供的测试图占位，imageKey 为空的商品走无图 fallback
    imageUrl: imageKey ? `/static/images/products/${imageKey}.jpg` : '',
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

/**
 * 限时抢购选品。
 *
 * 活动价来自**活动配置**（规划 §9.2 FLASH_SALE「活动价」配置项），
 * 不是对商品基础价的改写——商品 `price` 语义保持不变，
 * 客户端只负责展示服务端下发的活动价，不参与任何折扣计算（规划 §38.6）。
 */
const FLASH_PICK = [
  [5002, '3.20'],
  [5005, '2.40'],
  [5012, '4.90'],
  [5017, '5.80'],
  [5020, '4.40'],
  [5026, '2.90'],
];

function flashItem(skuId, activityPrice) {
  const p = MOCK_PRODUCTS.find((x) => x.skuId === skuId);
  if (!p) {
    return null;
  }
  return {
    skuId: p.skuId,
    productName: p.productName,
    spec: p.spec,
    unit: p.unit,
    imageUrl: p.imageUrl,
    price: p.price,
    activityPrice,
    stockStatus: p.stockStatus,
    availableQty: p.availableQty,
    isNonStandard: p.isNonStandard,
  };
}

/** 一级分类图标（mock 占位，规划 §9.2 CATEGORY_NAV 的「图标」配置项） */
const CATEGORY_ICON = {
  蔬菜: 'veg',
  水果: 'fruit',
  肉禽蛋: 'meat',
  水产: 'seafood',
  粮油: 'grain',
  调味: 'condiment',
};

export const MOCK_HOME_FLOORS = [
  { type: 'NOTICE', content: '中秋备货高峰，请提前 1 天下单，配送时效以实际为准。' },
  {
    type: 'CATEGORY_NAV',
    items: MOCK_CATEGORY_TREE.map((c) => ({
      categoryId: c.categoryId,
      categoryName: c.categoryName,
      iconUrl: CATEGORY_ICON[c.categoryName] ? `/static/images/category/${CATEGORY_ICON[c.categoryName]}.jpg` : '',
    })),
  },
  {
    type: 'BANNER',
    items: [
      {
        bannerId: 9001,
        eyebrow: '源头直采',
        title: '新鲜食材 助力餐饮好味道',
        subtitle: '品类齐全 · 稳定供应 · 次日直达',
        ctaText: '立即采购',
        imageUrl: '/static/images/home/banner.jpg',
        linkType: 'CATEGORY',
        linkValue: 100,
        sort: 1,
      },
    ],
  },
  {
    type: 'FLASH_SALE',
    title: '限时抢购',
    subtitle: '每日精选 · 超值低价',
    // 活动结束时间：相对当前时间 +6h，保证 demo 恒为「进行中」
    endTime: new Date(Date.now() + 6 * 60 * 60 * 1000).toISOString(),
    items: FLASH_PICK.map(([skuId, price]) => flashItem(skuId, price)).filter(Boolean),
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
    // 刻意与商品流第一页（5000–5009）错开，避免两个楼层看起来完全一样
    items: MOCK_PRODUCTS.slice(10, 16),
  },
];
