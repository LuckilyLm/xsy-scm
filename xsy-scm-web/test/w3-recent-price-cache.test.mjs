/**
 * Wave 3「历史价」缓存单测（修复计划 §5.1 必须新增的 5 条）。
 *
 * 钉死的是**串数据**而不是显示：缓存键一旦退化成行序号，换商品、删行上移、换客户都会
 * 静默把上一个 SKU / 上一个客户的历史成交价显示成当前行的参考价，而参考价看着完全合理。
 */
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {
  createRecentPriceCache,
  hasRecentPrices,
  isLoadingRecentPrice,
  loadRecentPrices,
  recentPriceKey,
  recentPriceRows,
} from '../src/views/business/scm/order/order-form-model.ts';

const APPLE = 101, BANANA = 102, CUSTOMER_A = 7, CUSTOMER_B = 8;
const priceOf = (skuId, unitPrice) => [{itemId: skuId, skuId, orderNo: `SO${skuId}`, unitPrice}];

/** 记录调用次数的假接口：断言「该请求时必须请求」只能靠计数。 */
function spy(rows) {
  const calls = [];
  return {calls, fetcher: async (skuId) => { calls.push(skuId); return rows; }};
}

test('同一行换商品（Apple → Banana）：必须按新 SKU 重新查询，不能复用旧缓存', async () => {
  const cache = createRecentPriceCache();
  const apple = spy(priceOf(APPLE, '6.50'));
  const banana = spy(priceOf(BANANA, '3.20'));
  await loadRecentPrices(cache, CUSTOMER_A, APPLE, () => apple.fetcher(APPLE));
  // 行号没变、只有 skuId 变了——按行号缓存正是在这里串数据
  const ok = await loadRecentPrices(cache, CUSTOMER_A, BANANA, () => banana.fetcher(BANANA));
  assert.ok(ok);
  assert.deepEqual(banana.calls, [BANANA], '换商品后没有重新请求历史价');
  assert.equal(recentPriceRows(cache, CUSTOMER_A, BANANA)[0].unitPrice, '3.20');
  assert.equal(recentPriceRows(cache, CUSTOMER_A, APPLE)[0].unitPrice, '6.50', '旧 SKU 缓存被覆盖');
});

test('删除第一行后第二行上移：上移行仍取自身 SKU 的历史价', async () => {
  const cache = createRecentPriceCache();
  await loadRecentPrices(cache, CUSTOMER_A, APPLE, async () => priceOf(APPLE, '6.50'));
  const rows = [{skuId: APPLE}, {skuId: BANANA}];
  rows.shift();                                   // 移除第一行，BANANA 从 index 1 变成 index 0
  const bananaCalls = [];
  await loadRecentPrices(cache, CUSTOMER_A, rows[0].skuId, async () => {
    bananaCalls.push(rows[0].skuId);
    return priceOf(BANANA, '3.20');
  });
  assert.deepEqual(bananaCalls, [BANANA], '上移后的行拿到了前一行的缓存（没重新请求）');
  assert.equal(recentPriceRows(cache, CUSTOMER_A, BANANA)[0].unitPrice, '3.20');
});

test('切客户（A → B）同 SKU：必须重新查询，不把 A 的成交价显示给 B', async () => {
  const cache = createRecentPriceCache();
  const calls = [];
  const fetcher = async () => { calls.push(calls.length + 1); return priceOf(APPLE, '6.50'); };
  await loadRecentPrices(cache, CUSTOMER_A, APPLE, fetcher);
  await loadRecentPrices(cache, CUSTOMER_B, APPLE, fetcher);
  assert.equal(calls.length, 2, '换客户后复用了上一个客户的历史价缓存');
  assert.notEqual(recentPriceKey(CUSTOMER_A, APPLE), recentPriceKey(CUSTOMER_B, APPLE));
});

test('同客户同 SKU 复用缓存：第二次点击不再请求', async () => {
  const cache = createRecentPriceCache();
  let calls = 0;
  const fetcher = async () => { calls += 1; return priceOf(APPLE, '6.50'); };
  assert.equal(await loadRecentPrices(cache, CUSTOMER_A, APPLE, fetcher), true);
  assert.equal(await loadRecentPrices(cache, CUSTOMER_A, APPLE, fetcher), true);
  assert.equal(calls, 1, '同一客户同一 SKU 重复请求');
});

test('请求失败：加载态必须落回、缓存不落，下次点击可重试', async () => {
  const cache = createRecentPriceCache();
  let calls = 0;
  await assert.rejects(loadRecentPrices(cache, CUSTOMER_A, APPLE, async () => {
    calls += 1;
    throw new Error('network down');
  }), /network down/);
  assert.equal(isLoadingRecentPrice(cache, CUSTOMER_A, APPLE), false, '失败后该行永久处于加载态');
  assert.equal(hasRecentPrices(cache, CUSTOMER_A, APPLE), false, '失败结果被当成缓存写入');
  await loadRecentPrices(cache, CUSTOMER_A, APPLE, async () => { calls += 1; return priceOf(APPLE, '6.50'); });
  assert.equal(calls, 2, '失败后无法重试');
});

test('查过但确无历史价：区分「空结果」与「还没查」，且客户/SKU 缺失时不请求', async () => {
  const cache = createRecentPriceCache();
  let calls = 0;
  await loadRecentPrices(cache, CUSTOMER_A, APPLE, async () => { calls += 1; return []; });
  assert.equal(hasRecentPrices(cache, CUSTOMER_A, APPLE), true);
  assert.deepEqual(recentPriceRows(cache, CUSTOMER_A, APPLE), []);
  assert.equal(await loadRecentPrices(cache, null, APPLE, async () => { calls += 1; return []; }), false);
  assert.equal(await loadRecentPrices(cache, CUSTOMER_A, undefined, async () => { calls += 1; return []; }), false);
  assert.equal(calls, 1, '缺客户或缺 SKU 时仍然发起了请求');
  assert.equal(recentPriceRows(cache, null, APPLE).length, 0);
});
