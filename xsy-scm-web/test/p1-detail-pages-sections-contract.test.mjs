/**
 * 客户 / 供应商 / 订单三个详情页的分区契约测试。
 *
 * 三页原本都把「基本信息 + 账期 / 关联表」串成一长串 `a-descriptions` + `a-divider`，
 * 编码、时间与业务字段混在一起，没有分区锚点（核心业务信息优先、日志再次、系统信息最后）。
 *
 * 本文件钉住：
 * 1. <b>按业务 Section 分区</b>：每页核心业务段在前、系统信息段在最后。
 * 2. <b>不再用 `a-divider` 当分区标题</b>，只保留模态框内的合法分隔。
 * 3. <b>编码与时间下沉到系统信息段</b>，不再出现在第一个描述列表里。
 * 4. <b>数值走全局 `.scm-money` / `.scm-quantity`</b>，不留局部 `.amount` 样式。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

/** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/^\s*\/\/.*$/gm, '');
}

const customer = code('../src/views/business/scm/customer/customer-detail.vue');
const supplier = code('../src/views/business/scm/supplier/supplier-detail.vue');
const order = code('../src/views/business/scm/order/order-detail.vue');

/** 取某标题在源码里的位置；不存在即失败。 */
function posOf(source, title) {
    const index = source.search(new RegExp(`<h3[^>]*>${title}</h3>`));
    assert.ok(index >= 0, `缺少分区标题「${title}」`);
    return index;
}

test('客户详情按「经营概览 / 联系 / 归属 / 授信 / 系统信息」分区', () => {
    const sections = ['客户经营概览', '联系与地址', '归属关系', '授信与账期', '系统信息'];
    const positions = sections.map((title) => posOf(customer, title));
    // 顺序固定，系统信息在最后
    assert.deepEqual(positions, [...positions].sort((a, b) => a - b), '客户详情分区顺序不符');
    assert.ok(positions[positions.length - 1] > positions[0], '系统信息必须在核心业务段之后');
    // 时间与备注必须落在系统信息段内（label/value 结构用 dt，不再要求 descriptions 的 label 属性）
    const sysIndex = positions[positions.length - 1];
    const tail = customer.slice(sysIndex);
    assert.match(tail, />创建时间</);
    assert.match(tail, />更新时间</);
    assert.match(tail, />备注</);
    // 客户编码仍在系统信息段保留完整值（页头只是 secondary 呈现，不等于下沉掉）
    assert.match(tail, />客户编码</);
});

test('供应商详情按「概览 / 联系方式 / 地址与地图 / 采购与账期 / 关联商品 / 系统信息」分区', () => {
    const sections = ['概览', '联系方式', '地址与地图', '采购与账期', '系统信息', '已关联商品（快照）'];
    const positions = sections.map((title) => posOf(supplier, title));
    assert.deepEqual(positions, [...positions].sort((a, b) => a - b), '供应商详情分区顺序不符');
    const sysIndex = posOf(supplier, '系统信息');
    const tail = supplier.slice(sysIndex);
    assert.match(tail, /label="创建时间"/);
    assert.match(tail, /label="更新时间"/);
});

test('订单详情按「摘要 / 客户配送 / 金额 / 商品 / 操作记录 / 系统信息」分区', () => {
    const sections = ['状态与核心摘要', '客户与配送', '金额结算', '商品明细', '操作记录', '系统信息'];
    const positions = sections.map((title) => posOf(order, title));
    assert.deepEqual(positions, [...positions].sort((a, b) => a - b), '订单详情分区顺序不符');
    // 备注下沉到系统信息（原来是混在第一条描述列表里）
    const tail = order.slice(posOf(order, '系统信息'));
    assert.match(tail, /label="备注"/);
});

test('页面级分区不再用 a-divider 充当标题', () => {
    // 客户与供应商详情的页面分区全部改为 h3 标题
    assert.ok(!/a-divider/.test(customer), '客户详情仍有 a-divider 分区');
    assert.ok(!/a-divider/.test(supplier), '供应商详情仍有 a-divider 分区');
    // 订单详情只允许模态框内的合法分隔（授信检查弹窗里的一条），不得用作页面分区
    const dividerUses = (order.match(/a-divider/g) ?? []).length;
    assert.ok(dividerUses <= 1, `订单详情有 ${dividerUses} 处 a-divider，疑似页面分区未清理`);
});

test('三个详情页的数值统一走全局 .scm-money / .scm-quantity', () => {
    for (const [name, source] of [['客户详情', customer], ['供应商详情', supplier]]) {
        assert.ok(!/class="amount"/.test(source), `${name} 仍用局部 .amount 类`);
        assert.ok(!/\.amount\s*\{/.test(source), `${name} 仍定义局部 .amount 样式`);
    }
    assert.match(customer, /class="scm-money"/);
    assert.match(customer, /class="scm-quantity"/);
    assert.match(supplier, /class="scm-money"/);
});
