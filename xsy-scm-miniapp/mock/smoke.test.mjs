/*
 * Mock 契约层冒烟测试
 *
 * mock 现在是全部客户端开发的"数据源"，所以它本身也需要被验证，
 * 不能只靠"构建通过"就假定路由和分页逻辑是对的。
 *
 * 运行方式（无需任何测试框架，esbuild 打包后直接跑）：
 *
 *   node_modules/.bin/esbuild mock/smoke.test.mjs --bundle --format=esm \
 *     --platform=node --alias:@=./src --outfile=.tmp-smoke.mjs
 *   node .tmp-smoke.mjs
 *
 * 之所以要先打包：mock 源码用了 `@/` 别名与 ESM，Node 无法直接解析。
 */

/* ---------- 最小断言 ---------- */
let passed = 0;
const failures = [];

function check(name, condition, detail) {
  if (condition) {
    passed += 1;
    console.log(`  ✓ ${name}`);
  } else {
    failures.push(name);
    console.log(`  ✗ ${name}${detail ? ` — ${detail}` : ''}`);
  }
}

/* ---------- 桩：uni 运行时 ---------- */
const storage = new Map();
globalThis.uni = {
  getStorageSync: (k) => (storage.has(k) ? storage.get(k) : ''),
  setStorageSync: (k, v) => storage.set(k, v),
  removeStorageSync: (k) => storage.delete(k),
};

const { dispatchMock, listMockRoutes, USE_MOCK } = await import('./index.js');

const TOKEN_KEY = 'xsy_mall_token';
const call = (method, url, data) => dispatchMock(url, method, data);

console.log(`\nmock 路由共 ${listMockRoutes().length} 条，USE_MOCK=${USE_MOCK}\n`);

/* ============================ 认证 ============================ */

console.log('认证：');
const codeRes = await call('POST', '/scm/mall/auth/sms-code/13800001234');
check('发送验证码成功', codeRes.code === 1 && !!codeRes.data.code, JSON.stringify(codeRes));
check('验证码接口回显 mock 标记', codeRes.data.mock === true);

const badPhone = await call('POST', '/scm/mall/auth/sms-code/123');
check('非法手机号被拒', badPhone.code === 30001);

const wrongCode = await call('POST', '/scm/mall/auth/sms-login', {
  phone: '13800001234',
  code: '000000',
});
check('错误验证码登录失败', wrongCode.code === 40170);

const loginRes = await call('POST', '/scm/mall/auth/sms-login', {
  phone: '13800001234',
  code: codeRes.data.code,
});
check('正确验证码登录成功', loginRes.code === 1);
check('登录返回 token', !!loginRes.data.token);
check('登录返回客户名', loginRes.data.customerName === '海岸城门店');
check('登录返回可交易状态', loginRes.data.customerStatus === 'COOPERATING');

// 真实链路里由 store 写入 token，这里模拟同样的持久化
storage.set(TOKEN_KEY, loginRes.data.token);

const profile = await call('GET', '/scm/mall/auth/profile');
check('带令牌取到客户资料', profile.code === 1 && profile.data.customerId === 3001);

const shortPwd = await call('POST', '/scm/mall/auth/login', {
  loginName: 'demo',
  password: '123',
});
check('短密码被拒', shortPwd.code === 40170);

const pwdLogin = await call('POST', '/scm/mall/auth/login', {
  loginName: 'demo',
  password: 'Xsy@Demo2026',
});
check('账密登录成功', pwdLogin.code === 1);

/* ============================ 目录 ============================ */

console.log('\n目录：');
const cats = await call('GET', '/scm/mall/catalog/categories');
check('分类树返回 6 个一级分类', cats.code === 1 && cats.data.length === 6, `实际 ${cats.data?.length}`);
check('一级分类带子分类', cats.data[0].children.length === 4);
check('子分类 level 为 2', cats.data[0].children[0].level === 2);

const page1 = await call('GET', '/scm/mall/catalog/products', { pageNum: 1, pageSize: 10 });
check('商品分页返回 10 条', page1.code === 1 && page1.data.list.length === 10);
check('分页 total 正确', page1.data.total === 33, `实际 ${page1.data.total}`);
check('分页回显 pageNum', page1.data.pageNum === 1);

const page4 = await call('GET', '/scm/mall/catalog/products', { pageNum: 4, pageSize: 10 });
check('第 4 页只剩 3 条', page4.data.list.length === 3, `实际 ${page4.data.list.length}`);

// 蔬菜 = categoryId 100，其下 4 个子类
const veg = await call('GET', '/scm/mall/catalog/products', { categoryId: 100, pageSize: 50 });
check('一级分类含全部子类商品', veg.data.total === 15, `实际 ${veg.data.total}`);
check(
  '一级分类结果不含其他分类',
  veg.data.list.every((p) => ['叶菜类', '根茎类', '瓜果类', '菌菇类'].includes(p.categoryName))
);

const leaf = await call('GET', '/scm/mall/catalog/products', { categoryId: 1001, pageSize: 50 });
check('二级分类精确过滤', leaf.data.total === 5, `实际 ${leaf.data.total}`);

const kw = await call('GET', '/scm/mall/catalog/products', { keyword: '土豆' });
check('关键字搜索命中', kw.data.total === 1 && kw.data.list[0].productName.includes('土豆'));

const kwNone = await call('GET', '/scm/mall/catalog/products', { keyword: '不存在的商品xyz' });
check('无结果返回空列表', kwNone.code === 1 && kwNone.data.total === 0);

const detail = await call('GET', '/scm/mall/catalog/products/5000');
check('商品详情成功', detail.code === 1 && detail.data.skuId === 5000);
check('详情含配送说明', !!detail.data.deliveryTip);

const missing = await call('GET', '/scm/mall/catalog/products/99999');
check('不存在商品返回 30002', missing.code === 30002);

// 详情页的「购买数量」步进完全依赖这三个字段，缺一个页面就会退化成 1 件固定数量
check('详情含起订量', detail.data.minOrderQty >= 1, `实际 ${detail.data.minOrderQty}`);
check('详情含步进量', detail.data.stepQty >= 1, `实际 ${detail.data.stepQty}`);
check('详情含可售量', Number.isFinite(Number(detail.data.availableQty)), `实际 ${detail.data.availableQty}`);
check('详情含商品描述', !!detail.data.description);

const hot = await call('GET', '/scm/mall/catalog/hot-keywords');
check('热词返回数组', hot.code === 1 && Array.isArray(hot.data) && hot.data.length > 0);

const fav = await call('GET', '/scm/mall/catalog/favorites', { pageNum: 1, pageSize: 20 });
check('常购商品返回列表', fav.code === 1 && fav.data.list.length > 0);
check(
  '常购项都带 favorite 标记',
  fav.data.list.every((p) => p.favorite === true)
);

/* ============================ 价格 / 非标品契约 ============================ */

console.log('\n价格与非标品契约：');
check('价格为字符串', typeof page1.data.list[0].price === 'string');
check('带价格来源标签', !!page1.data.list[0].priceSourceLabel);
const unpriced = (await call('GET', '/scm/mall/catalog/products', { keyword: '榴莲' })).data.list[0];
check('无报价商品 priceSource 为 UNPRICED', unpriced.priceSource === 'UNPRICED');
check('无报价商品标签为「暂无报价」', unpriced.priceSourceLabel === '暂无报价');
const nonStd = (await call('GET', '/scm/mall/catalog/products', { keyword: '土豆' })).data.list[0];
check('非标品标记正确', nonStd.isNonStandard === true);
check('非标品详情含实重说明', (await call('GET', `/scm/mall/catalog/products/${nonStd.skuId}`)).data.nonStandardTip.length > 0);

/* ============================ 首页 ============================ */

console.log('\n首页：');
const home = await call('GET', '/scm/mall/home');
check('首页返回楼层', home.code === 1 && home.data.sections.length === 6);
check(
  '楼层含 CATEGORY_NAV',
  home.data.sections.some((s) => s.type === 'CATEGORY_NAV')
);
check(
  '楼层含 FAVORITE',
  home.data.sections.some((s) => s.type === 'FAVORITE')
);
check('首页带 store 信息', home.data.store.customerName === '海岸城门店');

/* ============================ 回落与登出 ============================ */

console.log('\n回落与登出：');
const unmatched = await call('GET', '/scm/mall/cart');
check('未覆盖路由返回 null（回落真实请求）', unmatched === null);

const wrongMethod = await call('DELETE', '/scm/mall/auth/profile');
check('方法不匹配返回 null', wrongMethod === null);

const logout = await call('POST', '/scm/mall/auth/logout');
check('登出成功', logout.code === 1);
storage.delete(TOKEN_KEY);
const afterLogout = await call('GET', '/scm/mall/auth/profile');
check('登出后 profile 返回会话失效码', afterLogout.code === 30007);

/* ============================ 汇总 ============================ */

console.log(`\n${'─'.repeat(48)}`);
if (failures.length) {
  console.log(`失败 ${failures.length} 项：`);
  failures.forEach((f) => console.log(`  · ${f}`));
  process.exitCode = 1;
} else {
  console.log(`全部通过：${passed} 项`);
}
