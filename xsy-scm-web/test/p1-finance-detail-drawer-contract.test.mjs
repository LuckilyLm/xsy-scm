/**
 * 财务详情抽屉七段分区的契约测试。
 *
 * `finance-detail-drawer.vue` 统一分区为：
 *   1 单据概要 / 2 对象信息 / 3 金额组成 / 4 核销·退款·红字关系 /
 *   5 来源单据 / 6 流水记录 / 7 系统信息，
 * 并强调「金额摘要应比 ID、编码更突出」。
 *
 * 改造前它只有 5 段，且金额与单号混在同一张 descriptions 里 —— 金额和 ID 视觉权重相同。
 * 本文件钉住三件事，防止被后续改动合并回去：
 *
 * 1. **段标题存在且顺序正确**：段名是用户找信息的唯一锚点，缺一段就等于丢掉一类事实。
 * 2. **金额组成用独立的高权重结构与 .scm-money**：不能退回 descriptions 里的普通一行。
 * 3. **系统信息不得凭空编造**：后端 VO 不暴露 createTime / updateTime / 创建人，
 *    因此第 7 段**必须不渲染**（而不是编字段）。后端补齐后再启用。
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

const drawer = code('../src/views/business/scm/finance/finance-detail-drawer.vue');

test('抽屉按固定顺序分区', () => {
    const expected = ['单据概要', '对象信息', '金额组成', '来源单据', '核销 / 红字关系', '流水记录'];
    const positions = expected.map((title) => {
        // h3 可能带 class（金额组成就是 detail-section--nested），不能写死 `<h3>`
        const index = drawer.search(new RegExp(`<h3[^>]*>${title}</h3>`));
        assert.ok(index >= 0, `抽屉缺少分区标题「${title}」`);
        return index;
    });
    // 顺序固定：金额组成在对象信息之后、来源单据之前
    const sorted = [...positions].sort((a, b) => a - b);
    assert.deepEqual(positions, sorted, '分区顺序不符');
});

test('金额组成比 ID / 编码更突出', () => {
    // 金额摘要走独立的 amount-grid 结构，而不是普通 descriptions 行
    assert.match(drawer, /class="amount-grid"/);
    assert.match(drawer, /amount-cell__value scm-money/);
    // 净额 / 有效金额放在摘要的第一格
    assert.match(drawer, /净应收 \/ 净应付/);
    assert.match(drawer, /有效金额/);
    // 单据概要段里不得再出现金额（金额已迁到金额组成段）
    const header = drawer.slice(drawer.indexOf('<h3>单据概要</h3>'), drawer.indexOf('<h3>对象信息</h3>'));
    assert.ok(!/label="金额"/.test(header), '单据概要段仍有「金额」行，金额未迁到金额组成段');
});

test('第 7 段系统信息不编造后端没有的字段', () => {
    // 后端 VO（FinanceReceivableVO 等）只有业务字段，没有审计字段
    assert.ok(!/title>系统信息/.test(drawer), '渲染了系统信息段，但后端未提供审计字段');
    for (const fabricated of ['createTime', 'updateTime', 'creatorName', '创建时间', '更新时间', '创建人']) {
        assert.ok(!drawer.includes(fabricated), `抽屉编造了后端未提供的系统字段：${fabricated}`);
    }
});

test('核销与红字合为同一段，仍各自带小标题', () => {
    const relation = drawer.slice(drawer.indexOf('<h3>核销 / 红字关系</h3>'));
    assert.match(relation, /核销记录/);
    assert.match(relation, /红字关联/);
    // 原来的两个独立 section 标题不得再作为顶级分区存在
    assert.ok(!/<h3>红字关联<\/h3>/.test(drawer), '红字关联仍是独立顶级分区');
    assert.ok(!/<h3>核销记录<\/h3>/.test(drawer), '核销记录仍是独立顶级分区');
});
