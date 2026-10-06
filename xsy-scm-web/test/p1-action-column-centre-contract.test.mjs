/**
 * §8 表格统一规范「操作列居中」的全树契约单测。
 *
 * §8 明确规定「操作：居中」「操作列固定右侧时，表头和按钮共用同一中心线」。
 * 项目已先后用 `bb77b10e centre all table headers` 与 `ffa95370 centre the remaining
 * action columns` 批量化过，但那一轮只覆盖「薄壳页面」，漏掉了写在
 * `components/` 下的子组件（可编辑明细表、面板、选择器）—— 与本轮前面
 * `ff710abe` 修商品配置表时踩到的是同一类漏网。
 *
 * 本文件按**对象块**判定，而不是按行匹配：`{title:'操作', dataIndex:'action',
 * align:'right'}` 与 `{title:'本次金额', align:'right'}, {title:'操作', dataIndex:'action',
 * align:'center'}` 必须区别对待，后者里的 `align: 'right'` 属于金额列，是合规的。
 * 按行 grep 会把后者误判成违规，因此这里用括号块截取。
 *
 * 钉住：
 * 1. 全 `views/business/scm` 树下，凡声明了 `dataIndex: 'action'` 的列对象，
 *    一律不得为 `align: 'right'`（要么 'center'，要么省略——ant 默认左对齐不适用于操作列，故要求显式 center）。
 * 2. 本轮修复过的 6 个文件不得回退。
 * 3. 操作列宽度仍受 §8「普通场景 120～160px」约束（个别 80/90 的历史小列允许，但不得再出现 300+）。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync, statSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const scmRoot = fileURLToPath(new URL('../src/views/business/scm', import.meta.url));

/** 递归收集 .vue 文件。 */
function vueFiles(dir) {
    const out = [];
    for (const entry of readdirSync(dir)) {
        const full = path.join(dir, entry);
        if (statSync(full).isDirectory()) {
            out.push(...vueFiles(full));
        } else if (entry.endsWith('.vue')) {
            out.push(full);
        }
    }
    return out;
}

/** 取每个声明 dataIndex:'action' 的列对象块（最近的 { 到其后第一个 }）。 */
function actionColumnBlocks(source) {
    const blocks = [];
    const re = /dataIndex:\s*'action'/g;
    let match;
    while ((match = re.exec(source)) !== null) {
        const start = source.lastIndexOf('{', match.index);
        const end = source.indexOf('}', match.index);
        if (start >= 0 && end >= 0) {
            blocks.push({
                block: source.slice(start, end + 1),
                line: source.slice(0, match.index).split('\n').length,
            });
        }
    }
    return blocks;
}

const files = vueFiles(scmRoot).map((full) => ({
    full,
    rel: path.relative(scmRoot, full).replace(/\\/g, '/'),
    source: readFileSync(full, 'utf8'),
}));

test('§8 全树操作列不得 align right', () => {
    const offenders = [];
    for (const {rel, source} of files) {
        for (const {block, line} of actionColumnBlocks(source)) {
            if (/align:\s*'right'/.test(block)) {
                offenders.push(`${rel}:${line} → ${block.replace(/\s+/g, ' ')}`);
            }
        }
    }
    assert.deepEqual(offenders, [], `以下操作列仍右对齐：\n${offenders.join('\n')}`);
});

test('§8 本轮修复的子组件不得回退', () => {
    const fixed = [
        'delivery/components/route-fulfillment-panel.vue',
        'delivery/components/route-orders-panel.vue',
        'delivery/components/route-planning-suggestion-panel.vue',
        'finance/finance-refund-picker.vue',
        'finance/finance-write-off-list.vue',
        'order/components/order-item-editable-table.vue',
        'purchase/components/purchase-order-item-editable-table.vue',
    ];
    for (const rel of fixed) {
        const file = files.find((f) => f.rel === rel);
        assert.ok(file, `文件不存在：${rel}`);
        const blocks = actionColumnBlocks(file.source);
        assert.ok(blocks.length > 0, `${rel} 未声明操作列，疑似结构被改`);
        for (const {block, line} of blocks) {
            assert.ok(!/align:\s*'right'/.test(block), `${rel}:${line} 操作列回退为右对齐`);
        }
    }
});

test('§8 操作列不得出现 300px 以上常驻宽度', () => {
    const offenders = [];
    for (const {rel, source} of files) {
        for (const {block, line} of actionColumnBlocks(source)) {
            const width = block.match(/width:\s*(\d+)/);
            if (width && Number(width[1]) > 300) {
                offenders.push(`${rel}:${line} width=${width[1]}`);
            }
        }
    }
    assert.deepEqual(offenders, [], `操作列过宽：\n${offenders.join('\n')}`);
});
