/**
 * P2（物流配送 L3：发车 / 订单级签收 / 完成线路）前端契约单测。
 *
 * 只钉「违背之后页面照样能跑、但业务事实已经错了」的那一类：
 *
 * 1. **发车带 `Idempotency-Key`，签收与完成不带**。发车重复提交会重复扣库存，后端为此专门做了
 *    幂等回放；签收 / 完成的签名里没有这个头，多加会把「别人先签了」的版本冲突掩盖成一次成功。
 * 2. **签收载荷用 `delivery_route_order` 的行版本，不是线路版本**。用线路版本等于拿线路版本去
 *    覆盖别人对这一行的签收，同一条线路因此无法并发签收，而且会静默丢掉冲突信号。
 * 3. **`outboundNo` 为 null 是成功**：整条线路实发 0 时不存在出库单，把它当失败会让用户重复发车。
 * 4. **打印仍是打印**：预览 GET + 两个计次 POST，面板里绝不出现发车 / 签收 / 完成入口。
 * 5. **权限码只有一个来源**：配送视图里的 `v-privilege` 必须落在 `use-delivery-permission.ts`
 *    声明的集合内，该集合又必须是后端 `@SaCheckPermission` / V64 的子集。
 * 6. **履约状态与分配状态是两件事**：行只按 `assignmentStatus === 'ACTIVE'` 筛，
 *    状态标签只按 `fulfillmentStatuses` 渲染，取值与 V63 的 CHECK 同集合。
 * 7. **数量与金额不做前端算术**，null 一律渲染 `—`（`0` 会被读成「实发 0」这个真实事实）。
 *
 * 扫描前剥掉注释：这些文件里大量出现「不冲减库存」「不代表发货确认」这类反例说明，
 * 不剥注释会把纪律文档本身判成违规。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';
import {
    fulfillmentStatuses,
    routeStatuses,
    SIGNABLE_FULFILLMENT,
    signResults,
} from '../src/views/business/scm/delivery/delivery-types.ts';

const API = '../src/api/business/scm/delivery-api.ts';
const TYPES = '../src/views/business/scm/delivery/delivery-types.ts';
const PERM = '../src/views/business/scm/delivery/use-delivery-permission.ts';
const VIEW = '../src/views/business/scm/delivery/route-detail.vue';
const PRINT_VIEW = '../src/views/business/scm/delivery/route-print.vue';
const OUTBOUND_VIEW = '../src/views/business/scm/inventory/inventory-outbound-list.vue';

/** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
}

const api = code(API);
const view = code(VIEW);
const outbound = code(OUTBOUND_VIEW);

/** 打印面板整块：`key="print"` 到它自己的闭合标签（非贪婪，中间没有嵌套同名 pane）。 */
const printPane = view.match(/<a-tab-pane key="print"[\s\S]*?<\/a-tab-pane>/)?.[0];
const fulfillmentPane = view.match(/<a-tab-pane key="fulfillment"[\s\S]*?<\/a-tab-pane>/)?.[0];

/** 配送域后端契约里的功能码（V43 / V55 / V64 的 `t_menu.web_perms`，与 `@SaCheckPermission` 同源）。 */
const CONTRACT_DELIVERY_PERMS = [
  'scm:delivery:route:query',
  'scm:delivery:route:add',
  'scm:delivery:route:update',
  'scm:delivery:route:plan',
  'scm:delivery:route:cancel',
  'scm:delivery:route:print',
  'scm:delivery:route:dispatch',
  'scm:delivery:route:complete',
  'scm:delivery:order:sign',
  'scm:delivery:scope:all:query',
  'scm:delivery:amount:query',
  'scm:delivery:driver:query',
  'scm:delivery:driver:edit',
  'scm:delivery:vehicle:query',
  'scm:delivery:vehicle:edit',
];

/** 取 deliveryApi 里某个方法的整行源码（用于按方法判载荷口径，不按「附近若干字符」判）。 */
function apiLine(method) {
  const line = api.match(new RegExp(`^\\s*${method}: .*`, 'm'))?.[0];
  assert.ok(line, `deliveryApi 缺 ${method}()`);
  return line.trim();
}

// ------------------------------------------------------------------ 幂等与载荷

test('发车是带 Idempotency-Key 的 POST；签收与完成只靠 version，绝不带该头', () => {
  assert.match(api, /'Idempotency-Key': key/);
  assert.match(api, /crypto\.randomUUID\(\)/);
  // 键的释放只在成功之后：失败保留同一 UUID，重试才会回放原结果而不是再生成一张出库单。
  assert.match(api, /idempotentKeys\.delete\(signature\)/);
  assert.ok(api.indexOf('idempotentKeys.delete(signature)') > api.indexOf('const result = await request'),
      '必须在请求返回之后才释放键');

  assert.equal(
      apiLine('dispatch'),
      'dispatch: (id: Id, version: number) => idempotentCommand<DispatchResult>(`/routes/${id}/dispatch`, {version}),'
  );
  // 后端 `sign` / `complete` 的签名里没有 Idempotency-Key 参数：带上就是把乐观锁冲突回放成成功。
  for (const method of ['sign', 'complete']) {
    const line = apiLine(method);
    assert.match(line, /=> call<string>\('post'/, `${method} 必须走普通 call()，不经过幂等通道`);
    assert.ok(!/idempotent|Idempotency/i.test(line), `${method} 不得发送 Idempotency-Key`);
  }
  assert.match(apiLine('sign'), /`\/routes\/\$\{id\}\/orders\/\$\{orderId\}\/sign`/);
  assert.match(apiLine('complete'), /`\/routes\/\$\{id\}\/complete`/);
});

test('签收载荷带的是行版本，异常原因在前端就拦住', () => {
  const submit = view.match(/async function submitSign\(\)[\s\S]*?\n\}/)?.[0];
  assert.ok(submit, '缺 submitSign()');
  assert.match(submit, /version: target\.version/, '签收必须回传 delivery_route_order 自己的版本');
  assert.ok(!/version: route\.version/.test(submit), '拿线路版本签收等于用线路版本覆盖别人的行编辑');
  assert.match(submit, /deliveryApi\.sign\(route\.id, target\.orderId/);
  // 异常无原因不发请求（后端 41118 同口径），且拦住的位置在发请求之前。
  const guard = submit.search(/result === 'EXCEPTION' && !reason/);
  assert.ok(guard > -1, '缺 EXCEPTION 原因必填的前端校验');
  assert.ok(guard < submit.search(/deliveryApi\.sign\(/), '校验必须在发请求之前');
  // SIGNED 的原因可选，但不能把空白串当原因写进去（库里 CHECK 用 btrim 判空）。
  assert.match(submit, /reason: reason \|\| undefined/);

  // 弹窗按结果分流：异常签收必须留下「不冲库存」这句口径，否则用户会以为拒收会自动回到库存。
  const modal = view.match(/<a-modal\s+v-model:open="signVisible"[\s\S]*?<\/a-modal>/)?.[0];
  assert.ok(modal, '缺签收弹窗');
  assert.match(modal, /signForm\.result === 'EXCEPTION'[\s\S]*?不冲减/);
  assert.match(modal, /:title="signResults\[signForm\.result\]\.label"/, '弹窗标题取自共享结果字典');
});

test('整条线路零实发时 outboundNo 为 null，那是成功不是失败', () => {
  const dispatch = view.match(/function dispatch\(\)[\s\S]*?\n\}/)?.[0];
  assert.ok(dispatch, '缺 dispatch()');
  assert.match(dispatch, /result\.data\.outboundNo/);
  assert.match(dispatch, /未生成出库单/, '零实发要把「为什么没有单号」讲清楚，否则用户会再发一次车');
  assert.ok(!/error\.value = .*outboundNo/.test(dispatch), '空单号不得走错误分支');
  assert.match(dispatch, /deliveryApi\.dispatch\(route\.id, route\.version\)/);
  // 类型层同样把 null 表达成合法值，而不是可选到看不见。
  const types = code(TYPES);
  assert.match(types, /outboundNo\?: string \| null;/);
});

// ------------------------------------------------------------------ 打印边界

test('打印保持原样：预览 GET + 两个计次 POST，面板里没有任何 L3 动作', () => {
  assert.ok(printPane, '缺打印面板');
  assert.match(api, /print: \(id: Id\) => call<RoutePrint>\('get', `\/routes\/\$\{id\}\/print`\)/);
  assert.match(printPane, /@click="recordPrint"/);
  for (const forbidden of [
    'dispatch',
    'complete',
    'openSign',
    'deliveryApi.sign',
    '发车',
    '签收',
    '完成线路',
    'inventory-outbound',
  ]) {
    assert.ok(!printPane.includes(forbidden), `打印面板出现了 L3 动作 ${forbidden}`);
  }
  // 预览弹窗是纯只读：它唯一的 deliveryApi 调用就是 GET 预览。
  const printView = code(PRINT_VIEW);
  const calls = [...printView.matchAll(/deliveryApi\.(\w+)\(/g)].map((m) => m[1]);
  assert.deepEqual(calls, ['print'], `发货单预览只允许 print()，实际调用了 ${calls}`);
});

// ------------------------------------------------------------------ 权限

/** 配送目录下的全部视图（含 components），权限码扫描必须覆盖整页而不是只看改过的文件。 */
function deliveryViews() {
  const dir = new URL('../src/views/business/scm/delivery/', import.meta.url);
  const files = [
    ...readdirSync(dir).filter((name) => name.endsWith('.vue')).map((name) => `../src/views/business/scm/delivery/${name}`),
    ...readdirSync(new URL('components/', dir))
        .filter((name) => name.endsWith('.vue'))
        .map((name) => `../src/views/business/scm/delivery/components/${name}`),
  ];
  return files.map((file) => [file, code(file)]);
}

/**
 * 权限模块是 composable（依赖 Pinia store），无法在 node 测试里 import；
 * 因此按源码解析它声明的码表。解析形状固定为 `KEY: 'code'`，改写法就要同步改这里。
 */
function declaredPerms() {
  const source = code(PERM);
  const block = source.match(/export const DELIVERY_PERM = \{[\s\S]*?\n\} as const;/)?.[0];
  assert.ok(block, '缺 DELIVERY_PERM 声明表');
  const map = {};
  for (const m of block.matchAll(/^\s*([A-Z_]+): '([^']+)',$/gm)) map[m[1]] = m[2];
  // 金额码复用了既有常量而非字面量，单独按导出常量核对。
  const amount = source.match(/export const DELIVERY_AMOUNT_PERM = '([^']+)'/);
  assert.ok(amount, '缺 DELIVERY_AMOUNT_PERM');
  if (/AMOUNT_QUERY: DELIVERY_AMOUNT_PERM/.test(block)) map.AMOUNT_QUERY = amount[1];
  const operateLog = source.match(/export const OPERATE_LOG_PERM = '([^']+)'/);
  assert.ok(operateLog, '缺 OPERATE_LOG_PERM');
  assert.ok(Object.keys(map).length >= 9, `权限码表只解析到 ${Object.keys(map).length} 条，说明扫描失效`);
  return {map, operateLog: operateLog[1]};
}

test('配送视图出现的权限码全部来自单一来源，且该来源是后端契约的子集', () => {
  const {map: DELIVERY_PERM, operateLog: OPERATE_LOG_PERM} = declaredPerms();
  const declared = [...Object.values(DELIVERY_PERM), OPERATE_LOG_PERM];
  for (const perm of Object.values(DELIVERY_PERM)) {
    const known = CONTRACT_DELIVERY_PERMS.includes(perm) || perm === OPERATE_LOG_PERM;
    assert.ok(known, `前端声明了后端不存在的权限码 ${perm}：按钮能点、接口必 403`);
  }
  assert.ok(CONTRACT_DELIVERY_PERMS.includes(OPERATE_LOG_PERM) || OPERATE_LOG_PERM === 'support:operateLog:query');
  // L3 三个新码必须在册，漏一个的表现是「页面按超管联调全绿、正式角色永远看不到按钮」。
  assert.equal(DELIVERY_PERM.ROUTE_DISPATCH, 'scm:delivery:route:dispatch');
  assert.equal(DELIVERY_PERM.ORDER_SIGN, 'scm:delivery:order:sign');
  assert.equal(DELIVERY_PERM.ROUTE_COMPLETE, 'scm:delivery:route:complete');
  assert.equal(DELIVERY_PERM.AMOUNT_QUERY, 'scm:delivery:amount:query');

  const used = new Set();
  const dynamic = new Set();
  for (const [, source] of deliveryViews()) {
    for (const m of source.matchAll(/v-privilege="([^"]+)"/g)) {
      const raw = m[1];
      if (raw.startsWith("'")) {
        used.add(raw.slice(1, -1));
        continue;
      }
      const constRef = raw.match(/^DELIVERY_PERM\.([A-Z_]+)$/);
      if (constRef) {
        assert.ok(DELIVERY_PERM[constRef[1]], `模板引用了不存在的权限常量 DELIVERY_PERM.${constRef[1]}`);
        used.add(DELIVERY_PERM[constRef[1]]);
        continue;
      }
      dynamic.add(raw);
    }
    // 脚本里的可见性判定同样只能取自这份声明，不再抄第二份字面量。
    assert.ok(!/hasPerm\('[^']+'\)/.test(source), 'hasPerm 必须传 DELIVERY_PERM 常量');
    assert.ok(!/webPerms === 'scm:/.test(source), '不得在页面里手写作弊式 webPerms 比对');
  }
  assert.ok(used.size >= 9, `只解析到 ${used.size} 个权限码，说明页面或扫描方式失效`);
  for (const perm of used) {
    assert.ok(declared.includes(perm), `页面用了未声明的权限码 ${perm}`);
  }
  // 动态拼接的绑定逐个登记在册：新增一个就必须在这里交代，否则它绕过了单一来源。
  assert.deepEqual([...dynamic], ['editPermission'], 'master-list 的 driver/vehicle:edit 是唯一允许的动态绑定');
});

// ------------------------------------------------------------------ 状态机与字典

test('履约状态取自共享字典，取值与 V63 CHECK 同集合', () => {
  assert.deepEqual(Object.keys(fulfillmentStatuses), ['PENDING', 'IN_TRANSIT', 'SIGNED', 'EXCEPTION']);
  assert.deepEqual(Object.keys(signResults), ['SIGNED', 'EXCEPTION']);
  assert.deepEqual([...SIGNABLE_FULFILLMENT], ['IN_TRANSIT']);
  assert.ok(fulfillmentPane, '缺履约面板');
  assert.match(fulfillmentPane, /fulfillmentStatuses\[record\.fulfillmentStatus as FulfillmentStatus\]\.color/);
  assert.match(fulfillmentPane, /fulfillmentStatuses\[record\.fulfillmentStatus as FulfillmentStatus\]\.label/);
  // 与 assignmentStatus 正交：分配状态只用来筛行，绝不作为履约标签或签收条件出现。
  assert.ok(!fulfillmentPane.includes('assignmentStatus'), '履约标签不得取自分配状态');
  assert.match(view, /\.filter\(\(order\) => order\.assignmentStatus === 'ACTIVE'\)/);
  // RELEASED 行（取消线路 / 移除订单留下）也随详情返回，混进履约视图会给出「还能签收」的假入口。
  assert.match(view, /const activeOrders = computed<RouteOrder\[\]>\(/);
});

test('按钮出现条件与后端状态机一致，且每个写动作都有二次确认与 busy 闸门', () => {
  assert.deepEqual(Object.keys(routeStatuses), ['DRAFT', 'PLANNED', 'DISPATCHED', 'COMPLETED', 'CANCELLED']);
  const tags = [...view.matchAll(/<a-button\b[\s\S]*?<\/a-button>/g)].map((m) => m[0]);
  const tagWith = (needle) => tags.find((tag) => tag.includes(needle));

  const dispatchTag = tagWith('DELIVERY_PERM.ROUTE_DISPATCH');
  const completeTag = tagWith('DELIVERY_PERM.ROUTE_COMPLETE');
  const signTag = tagWith('DELIVERY_PERM.ORDER_SIGN');
  assert.ok(dispatchTag && completeTag && signTag, '缺发车 / 完成 / 签收按钮');
  // 发车限 PLANNED、完成限 DISPATCHED：两者条件不同不是漏写，签收则限 DISPATCHED 的行级 IN_TRANSIT。
  assert.match(dispatchTag, /v-if="detail\.route\.status === 'PLANNED'"/);
  assert.match(completeTag, /v-if="detail\.route\.status === 'DISPATCHED'"/);
  assert.match(signTag, /v-if="signable\(record\)"/);
  for (const tag of [dispatchTag, completeTag, signTag]) {
    assert.match(tag, /:disabled="busy"/, '写动作必须被在途请求挡住');
  }
  assert.match(view, /function signable\(record: RouteOrder\)[\s\S]*?status === 'DISPATCHED'[\s\S]*?SIGNABLE_FULFILLMENT\.includes\(record\.fulfillmentStatus\)/);
  // 发车与完成都会改库存 / 关闭线路，二者必须走 Modal.confirm；签收走弹窗本身就是确认。
  const dispatchFn = view.match(/function dispatch\(\)[\s\S]*?\n\}/)?.[0];
  const completeFn = view.match(/function complete\(\)[\s\S]*?\n\}/)?.[0];
  assert.ok(dispatchFn && completeFn);
  assert.match(dispatchFn, /Modal\.confirm\(\{[\s\S]*?不可撤销/);
  assert.match(completeFn, /Modal\.confirm\(\{/);
  // 线路已完成后服务端会拒签收，这里不复制状态机判定：完成动作只由 41101 权威拒绝。
  assert.match(completeFn, /deliveryApi\.complete\(route\.id, route\.version\)/);
});

// ------------------------------------------------------------------ 空值与算术纪律

test('数量与金额不做前端算术，null 渲染为 —（不是 0）', () => {
  for (const [name, source] of [[API, api], [TYPES, code(TYPES)], [PERM, code(PERM)], [VIEW, view]]) {
    assert.ok(!/\bNumber\(/.test(source), `${name} 不该 Number() 后端定点数`);
    assert.ok(!/toFixed\(/.test(source), `${name} 不该重排精度`);
    assert.ok(!/parseFloat\(/.test(source), `${name} 不该把定点串转成浮点再算`);
    assert.ok(!/\.reduce\(|\+=/.test(source), `${name} 出现了前端聚合`);
  }
  // 空单号 / 空签收时间 / 空原因都必须是 —；0 在这些列里是一个真实存在的事实，不能被当作空值复用。
  assert.match(view, />—（整条线路实发为 0，未生成出库单）</);
  assert.match(fulfillmentPane, /\{\{ datetime\(record\.signedAt\) \}\}/);
  assert.match(fulfillmentPane, /record\.signedBy \|\| '—'/);
  assert.match(fulfillmentPane, /record\.signReason \|\| '—'/);
  assert.ok(!/\|\| 0\b/.test(view), '空值不得回落成 0');
  // 对齐口径（AGENTS §12）：状态居中、操作右。
  assert.match(view, /title: '履约状态', dataIndex: 'fulfillmentStatus', width: 110, align: 'center' as const/);
  assert.match(view, /title: '操作', dataIndex: 'action', width: 160, align: 'right' as const/);
});

// ------------------------------------------------------------------ 跨页跳转

test('发车产生的出库单可跳到库存出库页，且目标页真的按单号筛选', () => {
  // 跳转与 deep-link 消费必须成对存在：只写一侧的表现是「点单号跳过去看到全量列表」，
  // 用户会以为这张单不存在。
  assert.match(view, /path: '\/inventory\/inventory-outbound-list', query: \{outboundNo\}/);
  assert.match(outbound, /const OUTBOUND_DEEP_LINK = \{outboundNo: null\}/);
  assert.match(outbound, /queryForm\.outboundNo = deepLinkFilters\(incomingQuery, OUTBOUND_DEEP_LINK\)\.outboundNo/);
  // 首屏就要落条件，不能等 watch。
  const mounted = outbound.match(/onMounted\(async \(\) => \{[\s\S]*?\}\)/)?.[0];
  assert.ok(mounted, '缺 onMounted');
  assert.match(mounted, /applyDeepLink\(route\.query\)[\s\S]*?queryData\(\)/);
  // 配送页只带走编号，不复制出库数量 / 金额口径。
  assert.ok(!/inventory-outbound-list[^\n]*(quantity|amount)/.test(view));
});

// ------------------------------------------------------------------ 新面板的三态

test('履约面板具备加载 / 空 / 错三态，与打印面板同一套做法', () => {
  assert.match(fulfillmentPane, /:loading="loading"/);
  assert.match(fulfillmentPane, /:locale="\{emptyText: fulfillmentEmptyText\}"/);
  assert.match(view, /const fulfillmentEmptyText = computed\(\(\) =>[\s\S]*?线路已取消[\s\S]*?线路还没有订单/);
  // 错误统一进顶部横幅（与打印的 loadPrint 一致），并由横幅的刷新动作恢复。
  assert.match(view, /<a-alert v-if="error" :message="error" type="error" show-icon/);
  assert.match(view, /<a-button @click="reload">刷新线路<\/a-button>/);
  // 面板是第五个 pane：不改动既有 base / orders / map / print 四个 pane 的键。
  const paneKeys = [...view.matchAll(/<a-tab-pane key="(\w+)"/g)].map((m) => m[1]);
  assert.deepEqual(paneKeys, ['base', 'orders', 'map', 'print', 'fulfillment']);
});
