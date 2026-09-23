/**
 * Wave 6「复制历史盘点」按页解析来源 SKU 单位的单测（修复计划 §4.1 / §12.2）。
 *
 * 钉死的是两个不报错的失效方式：一次拉 2000 行会被后端 `@Max(100)` 拒成 400（功能直接不可用），
 * 而「翻页时静默丢 SKU」会让复制出来的盘点单少几行却毫无提示（漏清点 = 账实差异）。
 *
 * 分页语义按真实接口造：只有**整页**才需要继续翻页，不足一页即视为最后一页。
 */
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {BALANCE_QUERY_PAGE_SIZE, resolveStocktakeCopyUnits} from '../src/views/business/scm/inventory/inventory-model.ts';

/** 把命中行补到整页（填充 SKU 不在来源里，只负责让这一页「读得完但还没找齐」）。 */
function fullPage(ids, seed) {
    const rows = ids.map((skuId) => ({skuId, unit: 'kg'}));
    let n = 0;
    while (rows.length < BALANCE_QUERY_PAGE_SIZE) {
        rows.push({skuId: `filler-${seed}-${n++}`, unit: 'kg'});
    }
    return rows;
}

/** 最后一页（不足整页）：读到它即收尾。 */
const lastPage = (ids) => ids.map((skuId) => ({skuId, unit: 'kg'}));

/** 记录每次请求页参数的假查询：断言「不许越过 pageSize 上限」「不该再翻页」只能靠看到的入参。 */
function fakeBalance(pages) {
    const requested = [];
    return {
        requested,
        fetchPage: async (pageNum, pageSize) => {
            requested.push({pageNum, pageSize});
            return pages[pageNum - 1] ?? [];
        },
    };
}

test('pageSize 恒等于后端上限 100，页码从 1 连续递增', async () => {
    const page = fakeBalance([fullPage([1], 1), fullPage([2], 2), lastPage([3])]);
    await resolveStocktakeCopyUnits([2, 3], page.fetchPage);
    assert.equal(BALANCE_QUERY_PAGE_SIZE, 100);
    assert.deepEqual(page.requested.map((r) => r.pageNum), [1, 2, 3]);
    for (const r of page.requested) {
        assert.equal(r.pageSize, 100, '复制盘点用了后端会拒掉的 pageSize');
    }
});

test('来源 SKU 提前找齐即停止翻页，不多打一次余额查询', async () => {
    const page = fakeBalance([fullPage([1, 2], 1), fullPage([3, 4], 2), lastPage([5])]);
    const {unitBySku, missingSkuIds} = await resolveStocktakeCopyUnits([1, 3], page.fetchPage);
    assert.deepEqual(page.requested.map((r) => r.pageNum), [1, 2], '找齐后仍在继续翻页');
    assert.deepEqual([...unitBySku.keys()].sort(), ['1', '3']);
    assert.deepEqual(missingSkuIds, []);
});

test('来源 SKU 分散在多页时逐个解析到单位，重复来源只解析一次', async () => {
    const page = fakeBalance([fullPage([1, 2], 1), lastPage([3])]);
    const {unitBySku, missingSkuIds} = await resolveStocktakeCopyUnits([3, 1, 3, 1], page.fetchPage);
    assert.deepEqual(missingSkuIds, []);
    assert.equal(unitBySku.get('1'), 'kg');
    assert.equal(unitBySku.get('3'), 'kg');
    assert.equal(page.requested.length, 2, '重复 SKU 造成额外的余额请求');
});

test('整页刚好 100 行时继续翻页，读到不足一页才收尾（不漏最后一页）', async () => {
    const page = fakeBalance([fullPage([5], 1), lastPage([900])]);
    const {unitBySku, missingSkuIds} = await resolveStocktakeCopyUnits([5, 900], page.fetchPage);
    assert.deepEqual(page.requested.map((r) => r.pageNum), [1, 2]);
    assert.equal(unitBySku.get('900'), 'kg');
    assert.deepEqual(missingSkuIds, []);
});

test('翻到最后一页仍缺的 SKU 显性返回，不静默丢行', async () => {
    const page = fakeBalance([lastPage([1, 2])]);
    const {unitBySku, missingSkuIds} = await resolveStocktakeCopyUnits([1, 2, 404, 405], page.fetchPage);
    assert.deepEqual(missingSkuIds, ['404', '405']);
    assert.equal(unitBySku.has('404'), false, '缺失 SKU 被填了兜底单位');
});

test('余额行缺 unit 时回落空串而不是编造单位', async () => {
    const page = fakeBalance([[{skuId: 7}]]);
    const {unitBySku} = await resolveStocktakeCopyUnits([7], page.fetchPage);
    assert.equal(unitBySku.get('7'), '');
});

test('来源行为空 skuId 时不参与解析，交由调用方整单拒绝', async () => {
    const page = fakeBalance([lastPage([1])]);
    const {unitBySku, missingSkuIds} = await resolveStocktakeCopyUnits([null, undefined, '', 1], page.fetchPage);
    assert.deepEqual(missingSkuIds, []);
    assert.equal(unitBySku.has('null'), false);
    assert.equal(unitBySku.has(''), false);
    assert.equal(unitBySku.get('1'), 'kg');
});
