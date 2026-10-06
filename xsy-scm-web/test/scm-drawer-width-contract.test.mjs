/**
 * SCM Drawer 宽度分级的契约单测。
 *
 * ## 为什么需要这份契约
 *
 * 迁移前 `views/business/scm` 的 34 个 `<a-drawer>` 里只有 4 个走公共分级，其余硬编码，
 * 取值散成 `620 / 760 / 860 / 900 / 920 / 1000 / 1080 / 1120 / 1180 / 1200 / 1240 /
 * 1280 / 1500` 外加 `min(NNNpx, 96vw)` —— 同一类页面宽度不同，改一次要改十几处，
 * 而且「视口兜底」和「宽度分级」被混成了同一件事。
 *
 * 现在统一为五档，业务页面一律 `scmDrawerWidth(...)`：
 *
 *   s          600   简单配置 / 简单维护，以及单列只读详情
 *   m          780   中型主数据 / 常规编辑表单
 *   l          940   复杂业务编辑表单
 *   xl        1120   复杂详情 / 内嵌宽表 / 多明细业务
 *   workspace 1440   <b>受限特殊档</b>，见第 4 项断言
 *
 * 长期准入规则见 `docs/architecture/scm-ui-guidelines.md` §5；
 * 当前 34 个实例及 workspace 白名单直接由本测试维护。
 *
 * ## 判定方式
 *
 * 只解析 `<a-drawer ...>` 开标签本身，不做全文件 grep —— 否则会误伤普通 CSS 里的
 * `width: 600px`、图表容器、输入框宽度等与抽屉无关的数值。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const scmRoot = fileURLToPath(new URL('../src/views/business/scm', import.meta.url));
const DRAWER_TS = '../src/theme/scm/scm-drawer.ts';
const RESPONSIVE_LESS = '../src/theme/scm/responsive.less';

/** 迁移完成时的实例基线：低于它就说明扫描方式失效了。 */
const EXPECTED_INSTANCES = 34;

/** 只允许这五个等级。 */
const ALLOWED_LEVELS = ['s', 'm', 'l', 'xl', 'workspace'];

/**
 * `workspace` 的准入白名单 —— 与 `docs/architecture/scm-ui-guidelines.md` §5 一致。
 * 每一类都必须能在代码注释里说出「为什么横向空间本身属于业务内容」。
 */
const WORKSPACE_FILES = new Set([
    'delivery/route-detail.vue',
    'sorting/components/sorting-task-detail-drawer.vue',
    'purchase/components/purchase-demand-batch-detail-drawer.vue',
    'report/report-components/report-drilldown-drawer.vue',
    'sorting/components/sorting-scale-drawer.vue',
]);

/** 递归收集 .vue 文件。 */
function vueFiles(dir) {
    const out = [];
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) out.push(...vueFiles(full));
        else if (entry.name.endsWith('.vue')) out.push(full);
    }
    return out;
}

/** 取每个 `<a-drawer` 的开标签文本（跨行，忽略引号内的 `>`）。 */
function drawerTags(source) {
    const out = [];
    const re = /<a-drawer\b/g;
    let match;
    while ((match = re.exec(source)) !== null) {
        let i = match.index + match[0].length;
        let inStr = null;
        for (; i < source.length; i++) {
            const ch = source[i];
            if (inStr) {
                if (ch === inStr) inStr = null;
                continue;
            }
            if (ch === '"' || ch === "'") { inStr = ch; continue; }
            if (ch === '>') break;
        }
        out.push({
            text: source.slice(match.index, i + 1),
            line: source.slice(0, match.index).split('\n').length,
        });
    }
    return out;
}

const files = vueFiles(scmRoot).map((full) => ({
    rel: path.relative(scmRoot, full).replace(/\\/g, '/'),
    source: readFileSync(full, 'utf8'),
}));

const instances = [];
for (const file of files) {
    for (const tag of drawerTags(file.source)) {
        const bound = tag.text.match(/:width="([^"]*)"/);
        const plain = tag.text.match(/(?:^|\s)width="([^"]*)"/);
        instances.push({
            rel: file.rel,
            line: tag.line,
            expr: (bound?.[1] ?? plain?.[1] ?? null),
            isBound: Boolean(bound),
        });
    }
}

// ------------------------------------------------------------------
// 5. 扫描基线（先钉住，后面几条才有意义）
// ------------------------------------------------------------------

test('扫描基线：Drawer 实例数不低于迁移完成时的基线', () => {
    assert.ok(
        instances.length >= EXPECTED_INSTANCES,
        `只枚举到 ${instances.length} 个 <a-drawer>，低于基线 ${EXPECTED_INSTANCES}，扫描方式可能已失效`,
    );
});

// ------------------------------------------------------------------
// 1 / 2 / 3. 必须走公共分级，不得硬编码
// ------------------------------------------------------------------

test('所有 a-drawer 都必须用 scmDrawerWidth 取值', () => {
    const offenders = instances
        .filter((i) => !i.isBound || !/^scmDrawerWidth\('(?:s|m|l|xl|workspace)'\)$/.test(i.expr.trim()))
        .map((i) => `${i.rel}:${i.line} width=${i.expr ?? '(未声明)'}`);
    assert.deepEqual(
        offenders,
        [],
        `以下抽屉没有通过 scmDrawerWidth 取宽度（禁止硬编码，也禁止 min(NNNpx, 96vw) ——\n` +
        `视口兜底由 theme/scm/responsive.less 统一负责）：\n${offenders.join('\n')}`,
    );
});

test('scmDrawerWidth 只接受 s / m / l / xl / workspace', () => {
    const offenders = [];
    for (const i of instances) {
        if (!i.isBound) continue;
        const level = i.expr.match(/scmDrawerWidth\('([^']*)'\)/)?.[1];
        if (level !== undefined && !ALLOWED_LEVELS.includes(level)) {
            offenders.push(`${i.rel}:${i.line} level=${level}`);
        }
    }
    assert.deepEqual(offenders, [], `出现了未定义的等级：\n${offenders.join('\n')}`);
});

test('不得再出现 min(NNNpx, Nvw) 形式的抽屉宽度', () => {
    const offenders = instances
        .filter((i) => /min\(\s*\d+px/.test(i.expr ?? ''))
        .map((i) => `${i.rel}:${i.line} width=${i.expr}`);
    assert.deepEqual(offenders, [], `视口兜底应由 responsive.less 统一提供：\n${offenders.join('\n')}`);
});

// ------------------------------------------------------------------
// 4. workspace 是受限档
// ------------------------------------------------------------------

test('workspace 只允许出现在已批准的 5 个文件里', () => {
    const offenders = instances
        .filter((i) => /scmDrawerWidth\('workspace'\)/.test(i.expr ?? ''))
        .filter((i) => !WORKSPACE_FILES.has(i.rel))
        .map((i) => `${i.rel}:${i.line}`);
    assert.deepEqual(
        offenders,
        [],
        `workspace 是受限特殊档，只允许地图工作台 / 分拣称重工作台 / 报表下钻 /\n` +
        `超宽业务数据阅读 / 多面板业务工作台五类；新增用例要先更新 SCM UI 规范并同步本契约：\n${offenders.join('\n')}`,
    );
});

test('已批准的 workspace 文件一个都不能少', () => {
    const used = new Set(
        instances
            .filter((i) => /scmDrawerWidth\('workspace'\)/.test(i.expr ?? ''))
            .map((i) => i.rel),
    );
    assert.deepEqual(
        [...WORKSPACE_FILES].filter((rel) => !used.has(rel)),
        [],
        'workspace 白名单里的文件丢失了 workspace 用法，疑似被误降级',
    );
});

test('每个 workspace 用例都写明了「为什么横向空间属于业务内容」', () => {
    for (const rel of WORKSPACE_FILES) {
        const file = files.find((f) => f.rel === rel);
        assert.ok(file, `文件不存在：${rel}`);
        assert.match(
            file.source,
            /<!--\s*workspace：/,
            `${rel} 用了 workspace 但没写明准入类别，后人无法判断它该不该在这里`,
        );
    }
});

// ------------------------------------------------------------------
// 等级定义与响应式兜底
// ------------------------------------------------------------------

test('scm-drawer.ts 定义了五档且取值正确', () => {
    const source = readFileSync(new URL(DRAWER_TS, import.meta.url), 'utf8');
    const expected = {s: 600, m: 780, l: 940, xl: 1120, workspace: 1440};
    for (const [level, width] of Object.entries(expected)) {
        assert.match(
            source,
            new RegExp(`\\b${level}:\\s*${width}\\b`),
            `scm-drawer.ts 缺少 ${level} = ${width}`,
        );
    }
    // workspace 的受限语义必须写在文件里，而不是只存在于文档
    assert.match(source, /不是普通.{0,6}第五档|受限特殊档/, 'scm-drawer.ts 未说明 workspace 的受限语义');
    for (const category of ['地图工作台', '分拣 / 称重工作台', '报表下钻', '超宽业务数据阅读', '多面板业务工作台']) {
        assert.ok(source.includes(category), `scm-drawer.ts 未列出 workspace 准入类别：${category}`);
    }
});

test('responsive.less 的 96vw 兜底仍然存在', () => {
    const source = readFileSync(new URL(RESPONSIVE_LESS, import.meta.url), 'utf8');
    assert.match(source, /\.ant-drawer-content-wrapper\s*\{[\s\S]{0,120}max-width:\s*96vw/,
        '抽屉的视口兜底丢了，workspace 会在大屏之外的场景溢出');
    assert.match(source, /\.ant-modal\s*\{[\s\S]{0,80}max-width:\s*96vw/,
        '弹窗的视口兜底丢了');
});
