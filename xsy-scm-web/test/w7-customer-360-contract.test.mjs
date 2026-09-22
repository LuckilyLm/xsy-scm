/**
 * Wave 7 客户 360° 上下文（customer-detail.vue 五 Tab）前端契约单测。
 *
 * 钉死这一波最容易被日后改动悄悄破坏、且破坏后不报错的前端边界（后端聚合与排除口径由
 * CustomerFrequentSkuIT 覆盖）：
 * 1. 常购商品走只读 GET 聚合（getRequest + days/limit），不是新建副本表也不是写命令；
 * 2. 五个上下文视图全部复用各领域既有查询接口（customer/order/pricing/visibility），
 *    不引入第二份事实，也不在详情里落任何写操作；
 * 3. 所有 Tab 锁定同一个 customerId（深链参数即唯一上下文）；
 * 4. 常购「数量」列语义是订购量而非结算 / 实重量、以 4 位小数字符串原样展示（不按金额格式化），
 *    且行按 SKU＋单位拆分、明确不跨单位求和；最近成交价缺失即空不兜底。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

/** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const API = '../src/api/business/scm/customer-api.ts';
const DETAIL = '../src/views/business/scm/customer/customer-detail.vue';

test('常购商品是只读 GET 聚合，带 days/limit，不新建写端点', () => {
  const api = code(API);
  assert.match(
    api,
    /frequentSkus:\s*\(customerId:\s*ScmId,\s*days\s*=\s*90,\s*limit\s*=\s*20\)\s*=>\s*getRequest\(`\/scm\/customer\/\$\{customerId\}\/frequent-skus`,\s*\{days,\s*limit\}\)/
  );
  // 只读：绝不能用 postRequest 打到 frequent-skus
  assert.doesNotMatch(api, /postRequest\(`\/scm\/customer\/\$\{customerId\}\/frequent-skus/);
});

test('五个上下文视图全部复用既有查询接口，详情侧不落任何写命令', () => {
  const vue = code(DETAIL);
  assert.match(vue, /customerApi\.detail\(/);
  assert.match(vue, /orderApi\.query\(\{[^}]*customerId:\s*customerId\.value/);
  assert.match(vue, /customerApi\.frequentSkus\(customerId\.value/);
  assert.match(vue, /pricingApi\.agreement\.query\(\{[^}]*customerId:\s*customerId\.value/);
  assert.match(vue, /customerVisibilityApi\.query\(\{[^}]*customerId:\s*customerId\.value/);
  // 360° 视图纯只读：不新增 / 编辑 / 删除 / 状态变更客户
  assert.doesNotMatch(vue, /customerApi\.(add|update|updateStatus|delete)\(/);
});

test('所有 Tab 锁定同一深链 customerId，切换客户整体复位、Tab 首次进入才加载', () => {
  const vue = code(DETAIL);
  // 唯一上下文来源：route.query.customerId 且必须是纯数字
  assert.match(vue, /route\.query\.customerId[\s\S]*?\/\^\\d\+\$\/\.test\(id\)/);
  // 非基础 Tab 复用同一 loader：仅当未加载且不在加载中才发请求
  assert.match(vue, /if\s*\(customerId\.value\s*&&\s*!loaded\.value\s*&&\s*!loading\.value\)\s*void reload\(\)/);
  // 换客户复位所有已加载 Tab，避免串上下文
  assert.match(vue, /watch\(customerId,\s*\(\)\s*=>\s*\{[\s\S]*?resetAllTabs\(\);[\s\S]*?loadBase\(\)/);
});

test('常购数量列语义是订购量、原样 4 位小数字符串展示、按单位分行不跨单位求和', () => {
  const vue = code(DETAIL);
  // 列标题为「订购量」而非结算 / 实重；且以纯文本展示，不按金额格式化（不加 ¥、不二次定点）
  assert.match(vue, /\{\s*title:\s*'订购量',\s*dataIndex:\s*'orderedQuantity'/);
  assert.match(vue, /\{\{\s*record\.orderedQuantity\s*\}\}/);
  assert.doesNotMatch(vue, /formatAmount(?:OrDash)?\(record\.orderedQuantity\)/);
  // 行键含单位：同 SKU 不同历史单位分行，绝不把不同单位的量相加成一个总量
  assert.match(vue, /row-key="\(r:\s*CustomerFrequentSku\)\s*=>\s*`\$\{r\.skuId\}-\$\{r\.unit\}`"/);
  assert.match(vue, /不跨单位求和/);
  // 最近成交价缺失即空（走 null 兜底为破折号的 formatAmountOrDash），不回退草稿 / 当前价
  assert.match(vue, /formatAmountOrDash\(record\.recentUnitPrice\)/);
});
