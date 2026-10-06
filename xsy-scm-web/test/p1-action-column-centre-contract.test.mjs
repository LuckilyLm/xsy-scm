/**
 * 操作列的表格统一规范在全树成立的契约测试。
 *
 * 规则：操作列居中；普通场景 120～160px，少数复杂工作台可放宽到 180px。
 * 项目已先后用 `bb77b10e centre all table headers`、`ffa95370 centre the remaining action
 * columns`、`ff8d2ce1` 与 `ff710abe` 批量化过，但每一轮都只覆盖了当时看见的那批页面。
 *
 * 本文件按对象块判定，而不是按行匹配：`{title:'本次金额', align:'right'}, {title:'操作',
 * dataIndex:'action', align:'center'}` 里的 `align:'right'` 属于金额列，是合规的。
 * 按行 grep 会把后者误判成违规，因此这里用括号块截取。
 *
 * 钉住五件事：
 * 1. 凡声明 `dataIndex:'action'` / `key:'action'` 的列对象，必须显式 `align:'center'`。
 *    省略不行 —— antd 默认左对齐，表头中心线与按钮组中心线会对不上。
 * 2. 操作列宽度不得超过 180px（复杂工作台的放宽上限）。
 * 3. 本轮收窄过的页面不得回退为超宽操作列。
 * 4. `ScmActionMore` 的权限裁剪链路仍然存在（菜单项挂不上 `v-privilege`，必须由调用方算 hidden）。
 * 5. 不允许用 `overflow: hidden` + `white-space: nowrap` 把按钮藏起来假装收窄成功。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync, statSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const scmRoot = fileURLToPath(new URL('../src/views/business/scm', import.meta.url));
const srcRoot = fileURLToPath(new URL('../src', import.meta.url));
const ACTION_MORE = fileURLToPath(
    new URL('../src/components/business/scm/scm-action-more/index.vue', import.meta.url),
);
const ACTION_ITEM = fileURLToPath(
    new URL('../src/components/business/scm/scm-action-more/action-item.ts', import.meta.url),
);

/** 复杂工作台的放宽上限。 */
const MAX_ACTION_WIDTH = 180;

/** 递归收集指定后缀的文件。 */
function filesWith(dir, ext) {
    const out = [];
    for (const entry of readdirSync(dir)) {
        const full = path.join(dir, entry);
        if (statSync(full).isDirectory()) out.push(...filesWith(full, ext));
        else if (entry.endsWith(ext)) out.push(full);
    }
    return out;
}

/**
 * 取每个操作列的对象块。
 *
 * 判定键同时接受 `dataIndex: 'action'` 与 `key: 'action'` —— 前者是主流写法，
 * 后者见于 `customer-sku-visibility-list`。不含 `title:'操作'` 的 `operationType`
 * 列（日志表的「操作类型」）不会被命中，因为它既不是 action 也不叫操作列。
 */
function actionColumnBlocks(source) {
    const blocks = [];
    const re = /(?:dataIndex|key):\s*'action'/g;
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

const files = filesWith(scmRoot, '.vue').map((full) => ({
    full,
    rel: path.relative(scmRoot, full).replace(/\\/g, '/'),
    source: readFileSync(full, 'utf8'),
}));

test('扫描基线有效：确实枚举到操作列', () => {
    const total = files.reduce((sum, f) => sum + actionColumnBlocks(f.source).length, 0);
    assert.ok(total >= 40, `只枚举到 ${total} 个操作列，扫描方式可能已失效`);
});

// ------------------------------------------------------------------
// 1. 居中
// ------------------------------------------------------------------

test('操作列必须显式 align center', () => {
    const offenders = [];
    for (const {rel, source} of files) {
        for (const {block, line} of actionColumnBlocks(source)) {
            if (!/align:\s*'center'/.test(block)) {
                offenders.push(`${rel}:${line} → ${block.replace(/\s+/g, ' ')}`);
            }
        }
    }
    assert.deepEqual(
        offenders,
        [],
        `以下操作列未显式居中（antd 默认左对齐，表头与按钮中心线会对不上）：\n${offenders.join('\n')}`,
    );
});

test('操作列不得右对齐', () => {
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

// ------------------------------------------------------------------
// 2. 宽度上限
// ------------------------------------------------------------------

test(`操作列宽度不得超过 ${MAX_ACTION_WIDTH}px`, () => {
    const offenders = [];
    for (const {rel, source} of files) {
        for (const {block, line} of actionColumnBlocks(source)) {
            const width = block.match(/width:\s*(\d+)/);
            if (width && Number(width[1]) > MAX_ACTION_WIDTH) {
                offenders.push(`${rel}:${line} width=${width[1]}`);
            }
        }
    }
    assert.deepEqual(
        offenders,
        [],
        `以下操作列超过 ${MAX_ACTION_WIDTH}px 常驻宽度：\n${offenders.join('\n')}`,
    );
});

// ------------------------------------------------------------------
// 3. 本轮收窄的页面不得回退
// ------------------------------------------------------------------

/** 文件 → 该文件内任何操作列都不允许超过的宽度。 */
const NARROWED = {
    'inventory/inventory-loss-gain-list.vue': 150,
    'inventory/inventory-outbound-list.vue': 150,
    'inventory/inventory-stocktake-list.vue': 150,
    'inventory/inventory-transfer-list.vue': 180,
    'inventory/inventory-conversion-list.vue': 160,
    'sorting/sorting-task-list.vue': 150,
    'promotion/promotion-coupon-list.vue': 150,
    'product/components/category-tree-table.vue': 160,
    'report/report-overview-list.vue': 110,
};

test('已收窄的操作列不得回退', () => {
    for (const [rel, limit] of Object.entries(NARROWED)) {
        const file = files.find((f) => f.rel === rel);
        assert.ok(file, `文件不存在：${rel}`);
        const blocks = actionColumnBlocks(file.source);
        assert.ok(blocks.length > 0, `${rel} 未声明操作列，疑似结构被改`);
        for (const {block, line} of blocks) {
            const width = block.match(/width:\s*(\d+)/);
            assert.ok(width, `${rel}:${line} 操作列缺少显式宽度`);
            assert.ok(
                Number(width[1]) <= limit,
                `${rel}:${line} 操作列回退为 ${width[1]}px（上限 ${limit}px）`,
            );
        }
    }
});

test('已修复的子组件不得回退', () => {
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
            assert.ok(/align:\s*'center'/.test(block), `${rel}:${line} 操作列丢了显式居中`);
        }
    }
});

// ------------------------------------------------------------------
// 4. ScmActionMore 的权限链路
// ------------------------------------------------------------------

test('ScmActionMore 本身不依赖 v-privilege（菜单项里指令不生效）', () => {
    const source = readFileSync(ACTION_MORE, 'utf8');
    assert.doesNotMatch(
        source,
        /v-privilege/,
        'ScmActionMore 渲染在 a-menu 里，v-privilege 对菜单项不生效，权限必须由调用方算 hidden',
    );
    // 必须仍然按 hidden 过滤
    assert.match(source, /\.filter\(\s*\(?\s*action\s*\)?\s*=>\s*!action\.hidden\s*\)/,
        'ScmActionMore 不再按 hidden 过滤，无权限动作会被显示出来');
});

test('ScmActionItem 仍保留 hidden / danger 语义', () => {
    const source = readFileSync(ACTION_ITEM, 'utf8');
    assert.match(source, /hidden\?:/, 'action-item 丢了 hidden 字段');
    assert.match(source, /danger\?:/, 'action-item 丢了 danger 字段');
    assert.match(source, /label:/, 'action-item 丢了 label 字段');
});

/**
 * 只读跳转型调用方：菜单项不涉及写操作，因此没有权限需要裁剪。
 * 例外必须显式登记，避免"忘记裁剪"被伪装成"只读跳转"。
 */
const READ_ONLY_MORE_CALLERS = new Set(['report/report-overview-list.vue']);

test('每个 ScmActionMore 调用方都用权限助手裁剪菜单项', () => {
    const callers = files.filter((f) => /ScmActionMore/.test(f.source));
    assert.ok(callers.length >= 10, `只找到 ${callers.length} 个调用方，扫描可能失效`);
    const offenders = callers
        .filter((f) => !READ_ONLY_MORE_CALLERS.has(f.rel))
        .filter((f) => !/hasPermission\(|hasPerm\(/.test(f.source))
        .map((f) => f.rel);
    assert.deepEqual(
        offenders,
        [],
        `以下调用方没有用 hasPermission / hasPerm 裁剪「更多」里的菜单项：\n${offenders.join('\n')}`,
    );
});

// ------------------------------------------------------------------
// 5. 平级入口不得人为分主次
// ------------------------------------------------------------------

test('平级的只读入口不人为分主次（报表总览下钻）', () => {
    const file = files.find((f) => f.rel === 'report/report-overview-list.vue');
    assert.ok(file, '文件不存在：report/report-overview-list.vue');
    // 四个维度完全平级，必须同住一个菜单，而不是挑两个提到行内
    for (const key of ['sales', 'purchase', 'receipt', 'inventory']) {
        assert.match(file.source, new RegExp(`\\{key: '${key}', label: '[^']+'\\}`),
            `下钻菜单缺 ${key}`);
    }
    assert.match(file.source, /<ScmActionMore label="下钻"/, '下钻应统一走 ScmActionMore');
    const cell = file.source.match(/column\.dataIndex === 'action'[\s\S]*?<\/template>/)?.[0];
    assert.ok(cell, '缺 action 单元格');
    assert.doesNotMatch(cell, /goAnalysis\(/,
        '行内不应再直接暴露单个下钻跳转，否则又变成了人为分主次');
});

// ------------------------------------------------------------------
// 6. 不允许用 CSS 假装收窄
// ------------------------------------------------------------------

test('不得用 overflow hidden + nowrap 把操作按钮藏起来冒充收窄', () => {
    const styleFiles = [
        ...filesWith(srcRoot, '.vue'),
        ...filesWith(srcRoot, '.less'),
        ...filesWith(srcRoot, '.css'),
    ];
    const offenders = [];
    for (const file of styleFiles) {
        const source = readFileSync(file, 'utf8');
        for (const match of source.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
            const selector = match[1];
            const body = match[2];
            // 只关心操作容器本身，不关心 .scm-tags 之类同样用 nowrap+hidden 的正当场景
            if (!/scm-table-actions|smart-table-operate/.test(selector)) continue;
            if (/overflow:\s*hidden/.test(body) && /white-space:\s*nowrap/.test(body)) {
                offenders.push(`${path.relative(srcRoot, file).replace(/\\/g, '/')} → ${selector.trim()}`);
            }
        }
    }
    assert.deepEqual(
        offenders,
        [],
        `以下样式把操作按钮裁掉而不是真正收窄：\n${offenders.join('\n')}`,
    );
});
