/**
 * W5（配送打印追踪）Wave 5 前端契约单测。
 *
 * 守的是打印计次这条 L0-L2 增量里**前端最容易悄悄违背、违背后又不报错**的几条：
 * 1. 双视角只读查询走 GET，正式生成打印走带 Idempotency-Key 的 POST；
 * 2. 「登记打印」只在可打印状态（PLANNED / DISPATCHED / COMPLETED）出现，且受打印权限约束；
 * 3. 打印状态三态（已打印 / 未打印 / 部分打印）标签与后端返回值一致；
 * 4. 生成打印绝不触碰库存出库、发车、GPS、签收等 L3 能力。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {printStatuses} from '../src/views/business/scm/delivery/delivery-types.ts';

/** 读源码并剥掉注释 —— 门禁只针对代码，Provenance 里提到被剪枝的名字是合规的。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const api = code('../src/api/business/scm/delivery-api.ts');
const view = code('../src/views/business/scm/delivery/route-detail.vue');

test('dual views are read-only GETs while formal printing is an idempotent POST', () => {
  assert.match(api, /call<RouteOrderView\[\]>\('get', `\/routes\/\$\{id\}\/orders-view`\)/);
  assert.match(api, /call<RouteCustomerView\[\]>\('get', `\/routes\/\$\{id\}\/customers-view`\)/);
  assert.match(api, /printCommand<PrintResult>\(`\/routes\/\$\{id\}\/print\/orders`/);
  assert.match(api, /printCommand<PrintResult>\(`\/routes\/\$\{id\}\/print\/customers`/);
  // 命令必须带 Idempotency-Key，失败保留同一 UUID、成功后换新键（与订单域一致）。
  assert.match(api, /'Idempotency-Key': key/);
  assert.match(api, /crypto\.randomUUID\(\)/);
  assert.match(api, /printKeys\.delete\(signature\)/);
});

test('printing is gated to printable states and print permission', () => {
  assert.match(view, /\['PLANNED', 'DISPATCHED', 'COMPLETED'\]\.includes/);
  assert.match(view, /scm:delivery:route:print/);
});

test('the delivery API exposes no L3 stock-outbound / GPS / sign-off endpoint', () => {
  // L0-L2：打印只是计次登记，接口层绝不提供库存出库 / GPS 追踪 / 签收 / 发车命令入口。
  // 视图里的「计划发车」只是线路表头既有展示字段（plannedDepartureTime），非新增 L3 动作，
  // 因此这一负向门禁只针对 API 端点集合，不针对整页文案。
  assert.doesNotMatch(api, /inventory|outbound|\bgps\b|track|sign-?off|签收|dispatch|departure/i);
});

test('print status labels match the three states the backend returns', () => {
  assert.deepEqual(Object.keys(printStatuses), ['PRINTED', 'UNPRINTED', 'PARTIAL']);
  assert.equal(printStatuses.PRINTED.label, '已打印');
  assert.equal(printStatuses.UNPRINTED.label, '未打印');
  assert.equal(printStatuses.PARTIAL.label, '部分打印');
});
