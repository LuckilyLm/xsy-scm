/**
 * Wave 4 §6.1「待办卡片带筛选 URL，目标页面必须真正消费」前端契约单测。
 *
 * 钉死三层：
 * 1. 解析器真实执行：白名单命中、非法值回落、缺参回落、重复键只取第一个；
 * 2. 跨端防漂移：直接读后端 `ScmTodoCardEnum` 的 route 字符串，要求其中每个查询键都被目标页面消费
 *    （日后给待办加参数而页面没接上，就是这里要拦住的断层——正是本次审计的原始缺陷）；
 * 3. 三个页面的进入模式统一：`[route.name, route.query]` watch + 名称守卫 + 无条件整体覆盖，
 *    所以 keep-alive 复用能重新套用新 query，普通菜单进入也不会残留上次 deep-link 条件。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {deepLinkFilters, deepLinkId} from '../src/lib/query-deep-link.ts';

/** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的写法是合规的。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const TODO_ENUM_JAVA =
  '../../xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/dashboard/constant/ScmTodoCardEnum.java';

const RECEIPT = '../src/views/business/scm/purchase/purchase-receipt-list.vue';
const LOSS_GAIN = '../src/views/business/scm/inventory/inventory-loss-gain-list.vue';
const ROUTES = '../src/views/business/scm/delivery/route-list.vue';

/** 页面路径 → 「deep-link 常量名 + 被覆盖的查询表单对象」。 */
const PAGES = [
  {path: '/purchase/purchase-receipt-list', file: RECEIPT, spec: 'RECEIPT_DEEP_LINK', form: 'queryForm'},
  {path: '/inventory/inventory-loss-gain-list', file: LOSS_GAIN, spec: 'LOSS_GAIN_DEEP_LINK', form: 'queryForm'},
  {path: '/delivery/routes', file: ROUTES, spec: 'ROUTE_DEEP_LINK', form: 'query'},
];

/** 后端待办枚举里带 query 的 route：路径 → 查询键集合。 */
function todoQueryKeys() {
  const java = readFileSync(new URL(TODO_ENUM_JAVA, import.meta.url), 'utf8');
  const keys = new Map();
  for (const match of java.matchAll(/"\/([^"]+)\?([^"]+)"/g)) {
    keys.set(
      '/' + match[1],
      new Set(match[2].split('&').map((pair) => pair.split('=')[0]))
    );
  }
  return keys;
}

/** 页面 deep-link 常量里声明的键。 */
function specKeys(vue, specName) {
  const block = new RegExp(`const ${specName} = \\{([\\s\\S]*?)\\};`).exec(vue);
  assert.ok(block, `${specName} 必须以模块级常量声明 deep-link 白名单`);
  return [...block[1].matchAll(/^\s*(\w+):/gm)].map((m) => m[1]);
}

test('解析器：白名单命中才落到筛选，非法值与空值按未提供处理', () => {
  const spec = {status: ['CONFIRMED', 'DRAFT'], receiptNo: null};
  assert.deepEqual(deepLinkFilters({status: 'CONFIRMED'}, spec), {status: 'CONFIRMED', receiptNo: undefined});
  // 自由文本键（null）只 trim，不校验取值
  assert.deepEqual(deepLinkFilters({receiptNo: '  PR2026  '}, spec), {status: undefined, receiptNo: 'PR2026'});
  // URL 可被手改：字典外的值绝不透传给查询接口
  assert.deepEqual(deepLinkFilters({status: "x' OR 1=1--"}, spec), {status: undefined, receiptNo: undefined});
  // 重复键只取第一个，不发明「多值 OR」语义
  assert.deepEqual(deepLinkFilters({status: ['DRAFT', 'CONFIRMED']}, spec).status, 'DRAFT');
});

test('解析器：query 为空时所有键回落 undefined（普通菜单进入不残留 deep-link）', () => {
  const spec = {status: ['PENDING'], lossGainNo: null};
  const filters = deepLinkFilters({}, spec);
  assert.deepEqual(filters, {status: undefined, lossGainNo: undefined});
  // 显式空串同样回落，而不是把「已清空」当成一个筛选值
  assert.equal(deepLinkFilters({status: '   '}, spec).status, undefined);
});

test('解析器：业务主键只接受纯数字串，且保持字符串不做数值转换', () => {
  assert.equal(deepLinkId({id: '1234567890123456789'}), '1234567890123456789');
  for (const raw of [{}, {id: ''}, {id: 'null'}, {id: '1e5'}, {id: '-3'}, {id: '12.5'}, {id: 'a1'}, {id: []}]) {
    assert.equal(deepLinkId(raw), undefined, `${JSON.stringify(raw)} 不是可用主键`);
  }
});

test('跨端一致：待办 route 的每个查询键都被目标页面 deep-link 白名单消费', () => {
  const todoKeys = todoQueryKeys();
  assert.ok(todoKeys.size >= 3, '待办枚举应至少有 3 张带筛选的卡片');
  for (const page of PAGES) {
    const required = todoKeys.get(page.path);
    assert.ok(required, `待办没有指向 ${page.path} 的卡片，PAGES 需要同步`);
    const declared = specKeys(code(page.file), page.spec);
    for (const key of required) {
      assert.ok(declared.includes(key), `${page.path} 的 deep-link 必须消费待办参数 ${key}`);
    }
  }
});

test('三个页面把解析结果整体写回查询表单，不做「有才覆盖」', () => {
  // 待办「待仓库确认入库」三个条件全部落到查询表单上——点进来必须还是那批数据
  const receipt = code(RECEIPT);
  for (const key of ['receiptNo', 'status', 'receiptMode', 'putawayStatus']) {
    assert.match(receipt, new RegExp(`queryForm\\.${key} = filters\\.${key};`), `收货单页缺 queryForm.${key}`);
  }
  // receiptMode / putawayStatus 必须有可见筛选控件，不能只存在表单状态里
  assert.match(receipt, /v-model:value="queryForm\.receiptMode"/);
  assert.match(receipt, /v-model:value="queryForm\.putawayStatus"/);

  const lossGain = code(LOSS_GAIN);
  assert.match(lossGain, /queryForm\.status = filters\.status;/);
  assert.match(lossGain, /queryForm\.lossGainNo = filters\.lossGainNo;/);
  // 重置与 deep-link 进入都回到页面默认，不永久保留旧条件
  assert.match(lossGain, /function resetQuery\(\)[\s\S]*?queryForm\.status = undefined;/);

  const routes = code(ROUTES);
  assert.match(routes, /query\.status = filters\.status;/);
  assert.match(routes, /function reset\(\)\s*\{\s*clearQuery\(\);/);

  for (const page of [receipt, lossGain, routes]) {
    assert.doesNotMatch(page, /filters\.\w+ \?\? /, '回退到旧值会让上一次的 deep-link 条件残留');
  }
});

test('三个页面用同一个进入模式：route.name 守卫 + route.query 依赖 + immediate', () => {
  for (const page of PAGES) {
    const vue = code(page.file);
    assert.match(vue, /import \{useRoute\} from 'vue-router';/);
    assert.match(vue, /const route = useRoute\(\);/);
    assert.match(
      vue,
      /watch\(\s*\[\(\) => route\.name, \(\) => route\.query\],/,
      `${page.path} 必须同时依赖 route.name 与整个 route.query（只盯单个键无法在重新进入时回落默认）`
    );
    assert.match(vue, /if \(name !== \w+RouteName\) return;/, `${page.path} 缺少缓存路由守卫，跳走时还会重复查询`);
    assert.match(vue, /\{immediate: true\}/, `${page.path} 首次进入必须应用 query`);
  }
  // 首挂载与复用只允许一条入口，否则会发两次查询请求
  assert.doesNotMatch(code(LOSS_GAIN), /onMounted/);
});
