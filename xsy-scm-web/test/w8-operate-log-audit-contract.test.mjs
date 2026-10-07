/**
 * 通用操作日志「业务上下文」与脱敏的前端契约测试。
 *
 * 钉死这一波破坏后不报错的前端边界（后端 STRPOS 精确匹配与读权限白名单由
 * OperateLogBusinessFilterPgIT / AdminOperateLogBusinessGuardTest 覆盖）：
 * 1. 脱敏是纯函数行为：password/token/secret 等命中键一律替换为占位符，未命中键原样保留，
 *    且递归覆盖嵌套对象与数组元素；
 * 2. 列表页接收并校验路由里的 businessType/businessId（白名单 + 正整数），
 *    首次加载、刷新、对象切换都重新套用，且重置不会残留业务上下文；
 * 3. 单个脏 response / userAgent 只退化该行，不整页失败；
 * 4. 客户 / 配送线路两个详情入口携带各自 businessType 跳到同一操作日志页。
 *    商品详情的入口已按产品要求移除（见下方用例注释），下钻机制本身仍有覆盖：
 *    `operate-log-list.vue` 的白名单与查询载荷校验未改动，客户与配送线路两条链路照常断言。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {maskSensitive} from '../src/views/support/operate-log/operate-log-mask.ts';

/** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const LIST = '../src/views/support/operate-log/operate-log-list.vue';
const MODAL = '../src/views/support/operate-log/operate-log-detail-modal.vue';
const CUSTOMER = '../src/views/business/scm/customer/customer-detail.vue';
const ROUTE_DETAIL = '../src/views/business/scm/delivery/route-detail-page.vue';

test('脱敏纯函数：敏感键替换为占位符、其余原样、递归覆盖对象与数组', () => {
  const input = {
    password: 'p@ss',
    token: 'abc',
    accessToken: 'x',
    refreshToken: 'y',
    apiKey: 'z',
    authorization: 'Bearer t',
    secret: 'shh',
    userName: 'alice',
    age: 30,
    nested: {pwd: 'q', keep: 'ok', list: [{sessionToken: 's', note: 'n'}]},
  };
  const masked = maskSensitive(input);
  // 命中键全部变成占位符
  for (const key of ['password', 'token', 'accessToken', 'refreshToken', 'apiKey', 'authorization', 'secret']) {
    assert.equal(masked[key], '******', `${key} 应被脱敏`);
  }
  assert.equal(masked.nested.pwd, '******');
  assert.equal(masked.nested.list[0].sessionToken, '******');
  // 未命中键保持原值与结构
  assert.equal(masked.userName, 'alice');
  assert.equal(masked.age, 30);
  assert.equal(masked.nested.keep, 'ok');
  assert.equal(masked.nested.list[0].note, 'n');
  // 顶层原始对象不能被就地改写
  assert.equal(input.password, 'p@ss');
});

test('列表页从路由解析并校验业务上下文，白名单外 / 非正整数视为无上下文', () => {
  const vue = code(LIST);
  assert.match(vue, /const BUSINESS_TYPES = \['PRODUCT', 'CUSTOMER', 'DELIVERY_ROUTE'\];/);
  assert.match(vue, /route\.query\.businessType/);
  assert.match(vue, /Number\.isInteger\(businessId\)\s*&&\s*businessId > 0/);
  assert.match(vue, /BUSINESS_TYPES\.includes\(businessType\)/);
});

test('业务上下文在首载、重置与对象切换三处都重新套用，旧筛选不得覆盖', () => {
  const vue = code(LIST);
  // businessType/businessId 属于查询态，重置时先整体复位再按当前路由重算
  assert.match(vue, /businessType:\s*undefined[\s\S]*?businessId:\s*undefined/);
  assert.match(vue, /onMounted\(\(\)\s*=>\s*\{[\s\S]*?applyBusinessContext\(\);[\s\S]*?ajaxQuery\(\);/);
  assert.match(vue, /function resetQuery\(\)[\s\S]*?Object\.assign\(queryForm, queryFormState\);[\s\S]*?applyBusinessContext\(\);/);
  // 刷新 / 换对象：监听路由业务参数，重新套用并回到第一页
  assert.match(vue, /watch\(\s*\(\)\s*=>\s*\[route\.query\.businessType,\s*route\.query\.businessId\]/);
  assert.match(vue, /applyBusinessContext\(\);\s*queryForm\.pageNum = 1;/);
});

test('逐行规范化：脏 response 退化为 null，单行异常不整页失败', () => {
  const vue = code(LIST);
  assert.match(vue, /function normalizeRow\(row[^)]*\)\s*\{[\s\S]*?try\s*\{[\s\S]*?JSON\.parse\(row\.response\)[\s\S]*?\}\s*catch\s*\(e\)\s*\{[\s\S]*?row\.response = null;/);
  assert.match(vue, /for \(const e of list\) \{\s*normalizeRow\(e\);\s*\}/);
});

test('详情弹窗展示前脱敏：参数与返回结果都过 maskSensitive', () => {
  const vue = code(MODAL);
  assert.match(vue, /import \{maskSensitive\} from '\.\/operate-log-mask';/);
  assert.match(vue, /maskedJson\(detail\.param\)/);
  assert.match(vue, /maskedJson\(detail\.response\)/);
  assert.match(vue, /return maskSensitive\(JSON\.parse\(raw\)\)/);
});

test('客户 / 配送线路详情各自带业务类型跳转到统一操作日志页', () => {
  // 商品详情曾有一个「操作日志」入口，已按产品要求连同「刷新详情」一起移除
  // （卡片自带 loading，出错时另有「重新加载」，刷新按钮冗余；操作日志则改由侧边栏进入）。
  // 因此这里不再把 PRODUCT 列为入口 —— 但不是把这条业务约束删掉：
  // 下钻机制、白名单与查询载荷校验仍由本文件其它用例 + E2E 覆盖。
  const entries = [
    [CUSTOMER, 'CUSTOMER'],
    [ROUTE_DETAIL, 'DELIVERY_ROUTE'],
  ];
  for (const [file, businessType] of entries) {
    const vue = code(file);
    assert.match(vue, /function openOperateLog\(\)/, `${file} 应有操作日志入口`);
    assert.match(vue, /path: '\/support\/operate-log\/operate-log-list'/, `${file} 应跳到操作日志列表`);
    assert.match(vue, new RegExp(`query: \\{businessType: '${businessType}'`), `${file} 业务类型应为 ${businessType}`);
    // 入口受操作日志查询权限约束。常驻按钮走 v-privilege；收进 ScmActionMore「更多」的
    // 菜单项挂不上指令（项目既有约定：菜单项一律用同一口径的 hasPermission 裁剪），
    // 因此两种写法都算合规，约束本身（必须受该权限码约束）不变。
    const gated = /v-privilege="'support:operateLog:query'/.test(vue)
      || /hasPermission\('support:operateLog:query'\)/.test(vue);
    assert.ok(gated, `${file} 操作日志入口未受权限约束`);
  }
});
