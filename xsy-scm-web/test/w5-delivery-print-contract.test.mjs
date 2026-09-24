/**
 * W5（配送打印追踪）Wave 5 前端契约单测。
 *
 * 守的是打印计次这条 L0-L2 增量里**前端最容易悄悄违背、违背后又不报错**的几条：
 * 1. 双视角只读查询走 GET，正式生成打印走带 Idempotency-Key 的 POST；
 * 2. 「登记打印」只在可打印状态（PLANNED / DISPATCHED / COMPLETED）出现，且受打印权限约束；
 * 3. 打印状态三态（已打印 / 未打印 / 部分打印）标签与后端返回值一致；
 * 4. 生成打印绝不越界：配送域不自己写库存、不接 GPS 轨迹（L3 的发车 / 签收是**另外**的端点，
 *    由 p2-delivery-l3-contract.test.mjs 钉住，打印这条线仍然只有预览与计次两个动作）。
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
  // P2 起打印与发车共用同一条幂等通道（`idempotentCommand`），端点与载荷本身没有变化。
  assert.match(api, /'Idempotency-Key': key/);
  assert.match(api, /crypto\.randomUUID\(\)/);
  assert.match(api, /idempotentKeys\.delete\(signature\)/);
});

test('printing is gated to printable states and print permission', () => {
  assert.match(view, /\['PLANNED', 'DISPATCHED', 'COMPLETED'\]\.includes/);
  assert.match(view, /scm:delivery:route:print/);
});

test('the delivery API still exposes no inventory write / GPS tracking endpoint', () => {
  // 本用例写于 L0–L2（当时连 dispatch / sign 都还不存在）。P2 L3 落地后，
  // 发车 / 签收 / 完成三个端点成为事实，由 p2-delivery-l3-contract.test.mjs 正向钉住它们的载荷口径。
  // 仍然成立、也仍然要害的那部分：配送域绝不自己写库存，也不接 GPS 轨迹 ——
  // 出库单是发车在**库存域内**生成的事实，配送侧只读它返回的单号。
  assert.doesNotMatch(api, /inventory|\bgps\b|track|sign-?off|departure|库存出库|轨迹/i);
  assert.ok(!/\/scm\/inventory|outboundApi|reservationApi/.test(api), '配送 API 不得直接触达库存域接口');
});

test('print status labels match the three states the backend returns', () => {
  assert.deepEqual(Object.keys(printStatuses), ['PRINTED', 'UNPRINTED', 'PARTIAL']);
  assert.equal(printStatuses.PRINTED.label, '已打印');
  assert.equal(printStatuses.UNPRINTED.label, '未打印');
  assert.equal(printStatuses.PARTIAL.label, '部分打印');
});
