/**
 * 前端后端字段缺口盘点的登记契约单测。
 *
 * `docs/architecture/scm-ui-guidelines.md` 要求 UI 不臆造后端尚未提供的业务事实，
 * 后端字段重构不得与 UI 改动同提交。因此执行中遇到的后端字段不足，
 * 一律登记到 `docs/plan/active/frontend-ui-backend-gap-inventory.md`（编号 B1…Bn），
 * 前端只做「不渲染该段 / 不臆造数值」。
 *
 * 本文件钉住登记的两端一致性（钉的是**引用完整性**，不是文案）：
 * 1. 代码注释里引用的 B 编号，必须在盘点文档里真实存在 —— 防止编号写错或文档删条后注释成孤儿。
 * 2. 盘点文档里列出的编号不得重复 —— 防止两条缺口共用一个编号导致引用歧义。
 * 3. 代码里登记过的缺口文件，仍必须保留指向文档的溯源注释（不得只留「属后端缺口」这种无指向的说法）。
 * 4. 关键证据锚点仍在：文档引用的 VO/类名必须真实存在于后端源码（防止证据漂移）。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync, existsSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

// test/ → xsy-scm-web/ → 仓库根
const repo = fileURLToPath(new URL('../../', import.meta.url));
const docPath = `${repo}/docs/plan/active/frontend-ui-backend-gap-inventory.md`;
const webRoot = `${repo}/xsy-scm-web`;
const backendJavaRoot = `${repo}/xsy-scm-server/sa-admin/src/main/java`;

const doc = readFileSync(docPath, 'utf8');

/** 后端生产源码里的全部类名（不含 `.java`），用于验证文档引用的证据锚点没有漂移。 */
function backendClassNames() {
    const names = new Set();
    (function walk(dir) {
        for (const entry of readdirSync(dir, {withFileTypes: true})) {
            if (entry.isDirectory()) walk(`${dir}/${entry.name}`);
            else if (entry.name.endsWith('.java')) names.add(entry.name.replace(/\.java$/, ''));
        }
    })(backendJavaRoot);
    return names;
}

/** 文档里声明的所有缺口编号（表格首列的 | B12 | 形式）。 */
function declaredIds() {
    return [...doc.matchAll(/^\|\s*(B\d+)\s*\|/gm)].map((m) => m[1]);
}

test('盘点文档存在且声明了 B 系列缺口编号', () => {
    assert.ok(existsSync(docPath), '缺少 docs/plan/active/frontend-ui-backend-gap-inventory.md');
    const ids = declaredIds();
    assert.ok(ids.length >= 5, `盘点文档只声明了 ${ids.length} 个缺口编号，疑似结构被破坏`);
    for (const expected of ['B1', 'B2', 'B3', 'B4', 'B5', 'B6', 'B7', 'B8']) {
        assert.ok(ids.includes(expected), `盘点文档缺少编号 ${expected}`);
    }
});

test('缺口编号不重复', () => {
    const ids = declaredIds();
    const seen = new Set();
    for (const id of ids) {
        assert.ok(!seen.has(id), `编号 ${id} 在盘点文档中出现多次`);
        seen.add(id);
    }
});

test('代码注释引用的 B 编号必须在盘点文档中存在', () => {
    const registered = [
        ['src/views/business/scm/order/order-return-list.vue', 'B6'],
        ['src/views/business/scm/promotion/promotion-coupon-list.vue', 'B7'],
        ['src/views/business/scm/inventory/inventory-reservation-list.vue', 'B8'],
        ['src/views/business/scm/finance/finance-detail-drawer.vue', 'B3'],
    ];
    for (const [file, id] of registered) {
        const source = readFileSync(`${webRoot}/${file}`, 'utf8');
        // 允许两种既有写法：全路径 `…-gap-inventory.md 的 Bn` 或简写「后端缺口盘点 Bn」。
        const cited = new RegExp(`(frontend-ui-backend-gap-inventory\\.md|缺口盘点)[\\s\\S]{0,80}${id}`).test(source)
            || new RegExp(`${id}[\\s\\S]{0,80}(frontend-ui-backend-gap-inventory\\.md|缺口盘点)`).test(source);
        assert.ok(cited, `${file} 未指向盘点文档的 ${id}`);
        assert.ok(declaredIds().includes(id), `盘点文档缺少被 ${file} 引用的 ${id}`);
    }
});

test('登记过的缺口文件不得退化为无指向的模糊说法', () => {
    for (const file of [
        'src/views/business/scm/order/order-return-list.vue',
        'src/views/business/scm/promotion/promotion-coupon-list.vue',
    ]) {
        const source = readFileSync(`${webRoot}/${file}`, 'utf8');
        assert.match(source, /frontend-ui-backend-gap-inventory\.md/, `${file} 丢失了缺口盘点溯源`);
    }
});

test('盘点文档引用的后端证据锚点真实存在', () => {
    // 从文档里抽 `` `XxxVO` `` / `` `XxxService` `` / `` `XxxEntity` `` 形式的类名，逐个到后端源码里找。
    // 不写死锚点清单：文档只保留「当前仍成立」的条目，条目一关就会被删，
    // 写死的清单会把「条目已关闭」误报成「证据漂移」。反过来，只要文档还引用着某个类名，
    // 它就必须真实存在 —— 这才是要钉的不变量。
    const named = [...new Set([...doc.matchAll(/`([A-Z][A-Za-z]*(?:VO|Service|Entity))`/g)].map((m) => m[1]))];
    assert.ok(named.length >= 1, '盘点文档没有引用任何后端类名，证据链已断（缺口的「当前缺口」列必须指向具体 VO/Service）');

    const existing = backendClassNames();
    for (const name of named) {
        assert.ok(existing.has(name), `盘点文档引用了后端不存在的类 ${name}（证据漂移）`);
    }
});

test('澄清节不得把「已具备的字段」误列为缺口', () => {
    // 流水行本身带 operator/createdAt，文档必须显式澄清，防止后续被误开单
    assert.match(doc, /operator[\s\S]{0,80}createdAt/);
    assert.match(doc, /不是缺口/);
});

test('已关闭的旧 UI 口径项仍被记录，防止被机械清理', () => {
    // F 系列（前端口径分歧）随 `.num` 收口一并关闭：条目可以从「当前缺口」表里删掉，
    // 但「为什么不再处理」必须留在文档里，否则下次审计会把它当成漏项重新登记。
    assert.match(doc, /已关闭的旧 UI 口径项/);
    assert.match(doc, /\.num/);
    assert.match(doc, /不再是后端缺口/);
});
