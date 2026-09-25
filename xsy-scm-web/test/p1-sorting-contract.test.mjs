/**
 * P1（分拣管理）前端契约单测。
 *
 * 这一波值得钉住的，都是「违背之后页面照样能跑、只是业务事实错了」的那一类：
 *
 * 1. **汇总页必须一个动作入口都没有**（硬产品规则）。聚合行没有主键，在它上面下写命令
 *    要么产生未定义的拆回行为，要么把导航伪装成命令；
 * 2. **录入载荷必须逐行带 `version`**。带任务版本等于「我用任务的版本号覆盖了别人的行编辑」，
 *    乐观锁会在别人改过时照样放行；
 * 3. **打印预览是 GET、登记计次才是 POST**。预览若走成 POST，每次刷新预览都会多算一次打印；
 *    建单与登记打印还要带 `Idempotency-Key`，录入 / 完成 / 取消 / 重开不带（后端签名里没有，
 *    多加只会掩盖版本冲突）；
 * 4. **权限码只有一个来源**。页面的 `v-privilege` 字面量必须全部落在 `sorting-const.ts`
 *    声明的集合内，而该集合又必须与后端契约逐字相同 —— 两处都对，才不存在「按钮能点、接口 403」；
 * 5. **数量不做前端算术**：`Number(` / `toFixed(` / `parseFloat(` 不得出现在两页里，
 *    `null` 渲染成 `—` 而不是 `0`；
 * 6. **状态机的按钮出现条件**与后端一致：取消限 WORKING、重开限 COMPLETED、
 *    打印限 PRINTABLE，且取消 / 重开的 `reason` 在前端就是必填。
 *
 * 扫描前剥掉注释：这些文件里大量出现「不得跨单位求和」这类反例说明，
 * 不剥注释会把纪律文档本身判成违规。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
    SCM_SORTING_OCCUPATION_ENUM,
    SCM_SORTING_PERMISSION,
    SCM_SORTING_PRINTABLE_STATUS,
    SCM_SORTING_PRODUCT_TYPE_ENUM,
    SCM_SORTING_RESULT_ENUM,
    SCM_SORTING_TABLE_ID,
    SCM_SORTING_TASK_STATUS_ENUM,
    SCM_SORTING_WORKING_STATUS,
} from '../src/constants/business/scm/sorting-const.ts';

const TASK_LIST = '../src/views/business/scm/sorting/sorting-task-list.vue';
const SUMMARY = '../src/views/business/scm/sorting/sorting-summary.vue';
const API = '../src/api/business/scm/sorting-api.ts';
const CONST = '../src/constants/business/scm/sorting-const.ts';

/** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/^\s*\/\/.*$/gm, '');
}

const taskList = code(TASK_LIST);
const summary = code(SUMMARY);
const api = code(API);

/** 后端 P1 契约里的九个权限码（与 `@SaCheckPermission` / V61 的 `web_perms` 同源）。 */
const CONTRACT_PERMS = [
    'scm:sorting:task:query',
    'scm:sorting:task:add',
    'scm:sorting:task:assign',
    'scm:sorting:item:update',
    'scm:sorting:task:complete',
    'scm:sorting:task:cancel',
    'scm:sorting:task:reopen',
    'scm:sorting:task:print',
    'scm:sorting:summary:query',
];

// ------------------------------------------------------------------ 只读边界

test('汇总页没有任何录入 / 写命令入口（裁决补充第 19 条）', () => {
    // 唯一的 sortingApi 调用必须是 summary 查询
    const calls = [...summary.matchAll(/sortingApi\.(\w+)\(/g)].map((m) => m[1]);
    assert.deepEqual(calls, ['summary'], `汇总页只允许查询，实际调用了 ${calls}`);
    for (const forbidden of ['enter', 'create', 'assign', 'complete', 'cancel', 'reopen', 'print', 'entry']) {
        assert.ok(!new RegExp(`\\b${forbidden}\\s*\\(`).test(summary), `汇总页出现了写动作 ${forbidden}`);
    }
    // 按「可点的动作」判，不按词判：页面提示文案里出现「录入 / 完成」是正确指引
    assert.ok(!/<a-button[^>]*>(确认入库|提交分拣|登记打印|新建)/.test(summary), '汇总页不得有动作按钮');
    assert.ok(!/a-drawer|a-modal|row-selection|Idempotency-Key/.test(summary), '汇总页不得有编辑弹窗或批量选择');
    // 只有查询、重置与错误横幅重试三个按钮，没有任何其它可点动作
    const buttons = [...summary.matchAll(/<a-button(\s[^>]*)>/g)].map((m) => m[1]);
    assert.equal(buttons.length, 3, `汇总页只该有查询 / 重置 / 重试三个按钮，实际 ${buttons.length}`);
    assert.ok(buttons.every((attrs) => /@click="(onSearch|resetQuery|queryData)"/.test(attrs)));
});

test('汇总页不跨单位求和：没有合计行、数量列只做 null 安全透传', () => {
    assert.ok(!/summary:\s*\(|Table SUMMARY|合计：?\s*\{\{/.test(summary), '没有后端跨行合计就不放前端自算的合计');
    assert.match(summary, /quantityText\(record\.plannedQuantity\)/);
    assert.match(summary, /quantityText\(record\.sortedQuantity\)/);
    // 分组键含单位，所以 rowKey 必须带单位；只按 skuId 会让同 SKU 的不同单位互相覆盖
    assert.match(summary, /r\.skuId\}-\$\{r\.saleUnitSnapshot/);
    assert.ok(!/reduce\(|\+=/.test(summary), '汇总页不做前端累加');
});

// ------------------------------------------------------------------ 模块依赖

test('两页都通过 sorting-api 访问后端，不各自拼 URL', () => {
    for (const [name, source] of [['任务页', taskList], ['汇总页', summary]]) {
        assert.match(source, /from '\/@\/api\/business\/scm\/sorting-api'/, `${name} 未导入 sorting-api`);
        assert.ok(!/url:\s*['"`]\/scm\/sorting/.test(source), `${name} 不得绕过 api 模块直接请求`);
    }
});

// ------------------------------------------------------------------ 录入载荷

test('录入载荷逐行带 version，且只提交改动过的行', () => {
    const builder = taskList.match(/function buildEntryPayload\(\)[\s\S]*?\n\}/)?.[0];
    assert.ok(builder, '缺 buildEntryPayload()');
    assert.match(builder, /version: record\.version/, '每行必须带回它自己读到的版本');
    assert.ok(!/version: (detail|task)\.value\.task\.version/.test(builder), '不得用任务版本代替行版本');
    assert.match(builder, /isDirty\(record\)/, '只提交脏行');
    assert.match(builder, /id: record\.id/);
    // 分拣量按 4 位定点字符串提交（后端入参是 BigDecimal，但字符串保留录入口径）
    assert.match(builder, /sortedQuantity: quantity/);
    // 非 NORMAL 结果必须有原因，否则不发请求
    assert.match(builder, /draft\.result !== SCM_SORTING_RESULT_ENUM\.NORMAL\.value/);
    assert.match(builder, /entryErrors\[key\]/);
    const entry = taskList.match(/async function submitEntry\(\)[\s\S]*?\n\}/)?.[0];
    assert.ok(entry);
    assert.match(entry, /sortingApi\.enter\(detail\.value\.task\.id, payload\)/);
});

// ------------------------------------------------------------------ 打印：预览 vs 计次

test('打印预览是只读 GET，登记计次才是带幂等键的 POST', () => {
    assert.match(api, /printPreview: \(id: Id\) => call<SortingPrint>\('get', `\/tasks\/\$\{id\}\/print`\)/);
    assert.match(api, /print: \(id: Id, form: SortingActionPayload\) => command<SortingPrintResult>\(`\/tasks\/\$\{id\}\/print`, form\)/);
    // 页面上预览与登记各走一条路，且预览函数里不许出现计次命令
    const preview = taskList.match(/async function loadPrintPreview\(\)[\s\S]*?\n\}/)?.[0];
    const record = taskList.match(/async function recordPrint\(\)[\s\S]*?\n\}/)?.[0];
    assert.ok(preview && record, '缺 loadPrintPreview() / recordPrint()');
    assert.match(preview, /sortingApi\.printPreview\(/);
    assert.ok(!/sortingApi\.print\(/.test(preview), '预览路径绝不调用计次命令');
    assert.match(record, /sortingApi\.print\(/);
    assert.match(record, /version: printVersion\.value/);
});

test('幂等键只加在建单与登记打印上，失败保留同一 UUID、成功即换新', () => {
    assert.match(api, /'Idempotency-Key': key/);
    assert.match(api, /crypto\.randomUUID\(\)/);
    assert.match(api, /idempotentKeys\.delete\(signature\)/);
    assert.match(api, /create: .*=> command<SortingTaskDetail>\('\/tasks', form\)/);
    // 其余命令的重复提交由行 / 任务乐观锁拦截，带幂等头只会把冲突掩盖成成功
    for (const path of ['assign', 'enter', 'complete', 'cancel', 'reopen']) {
        assert.match(api, new RegExp(`${path}: [^\\n]*=> call<`), `${path} 不应走幂等 command()`);
    }
});

// ------------------------------------------------------------------ 权限

test('页面用到的 v-privilege 全部落在权限码集合内，且集合与后端契约逐字相同', () => {
    const declared = Object.values(SCM_SORTING_PERMISSION).sort();
    assert.deepEqual(declared, [...CONTRACT_PERMS].sort(), '权限码必须与后端契约一一对应，不多不少');

    const used = new Set(
        [
            ...[...taskList.matchAll(/v-privilege="'([^']+)'"/g)].map((m) => m[1]),
            ...[...summary.matchAll(/v-privilege="'([^']+)'"/g)].map((m) => m[1]),
        ]
    );
    // 样本量门禁：匹配数为 0 会让上面两个断言「空跑通过」，扫描失效比断言失败更危险
    assert.ok(used.size >= 8, `只解析到 ${used.size} 个权限码，说明页面或扫描方式失效`);
    for (const perm of used) {
        assert.ok(declared.includes(perm), `页面用了未声明的权限码 ${perm}`);
    }
    // 脚本里的可见性判定同样只能取自这份声明
    for (const [name, source] of [['任务页', taskList], ['汇总页', summary]]) {
        for (const m of source.matchAll(/hasPermission\(SCM_SORTING_PERMISSION\.(\w+)\)/g)) {
            assert.ok(SCM_SORTING_PERMISSION[m[1]], `${name} 引用了不存在的权限常量 ${m[1]}`);
        }
        assert.ok(
            !/hasPermission\('[^']+'\)/.test(source),
            `${name} 的 hasPermission 必须传 SCM_SORTING_PERMISSION 常量，不要再写字面量第二份真相`
        );
    }
});

// ------------------------------------------------------------------ 数量与空值纪律

test('数量不在前端做数值运算', () => {
    for (const [name, source] of [[TASK_LIST, taskList], [SUMMARY, summary]]) {
        assert.ok(!/\bNumber\(/.test(source), `${name} 不该 Number() 后端定点数`);
        assert.ok(!/toFixed\(/.test(source), `${name} 不该重排精度`);
        assert.ok(!/parseFloat\(/.test(source), `${name} 不该把定点串转成浮点再算`);
    }
    // null 一律是 —；0 是「录过且为 0」，两者不能合并
    assert.match(code('../src/views/business/scm/sorting/sorting-types.ts'), /return value === null \|\| value === undefined \|\| value === '' \? '—' : value/);
});

test('分拣量输入按 4 位定点、允许 0（整行缺货是合法结果）', () => {
    const cell = taskList.match(/column\.dataIndex === 'sortedQuantity'[\s\S]*?<\/template>/)?.[0];
    assert.ok(cell, '缺分拣量单元格');
    assert.match(cell, /string-mode/);
    assert.match(cell, /:precision="4"/);
    assert.match(cell, /:min="'0'"/, 'min 必须允许 0，否则缺货只能靠不提交表达，任务永远无法完成');
});

// ------------------------------------------------------------------ 状态机与可见性

test('按钮出现条件与后端状态机一致', () => {
    assert.deepEqual(Object.keys(SCM_SORTING_TASK_STATUS_ENUM), ['PENDING', 'SORTING', 'COMPLETED', 'CANCELLED']);
    assert.deepEqual([...SCM_SORTING_WORKING_STATUS].sort(), ['PENDING', 'SORTING']);
    assert.deepEqual([...SCM_SORTING_PRINTABLE_STATUS].sort(), ['COMPLETED', 'SORTING']);
    assert.deepEqual(Object.keys(SCM_SORTING_RESULT_ENUM), ['NORMAL', 'SHORT', 'OUT_OF_STOCK', 'OVER']);
    assert.deepEqual(Object.keys(SCM_SORTING_OCCUPATION_ENUM), ['ACTIVE', 'RELEASED']);
    assert.deepEqual(Object.keys(SCM_SORTING_PRODUCT_TYPE_ENUM), ['STANDARD', 'NON_STANDARD']);

    // 取消限 WORKING、重开限 COMPLETED：两者条件不同不是漏写。
    // 按「按钮标签」取条件，不按「权限码附近 200 字符」取：一个码在列表行与抽屉里各出现一次，
    // 靠距离匹配会把抽屉那一份的条件安到列表按钮上，断言就只测了个寂寞。
    const actionTags = [...taskList.matchAll(/<a-button\b[\s\S]*?<\/a-button>/g)].map((m) => m[0]);
    const tagFor = (perm) => actionTags.find((tag) => tag.includes(perm));
    const cancelTag = tagFor('scm:sorting:task:cancel');
    const reopenTag = tagFor('scm:sorting:task:reopen');
    const printTag = tagFor('scm:sorting:task:print');
    assert.ok(cancelTag && reopenTag && printTag, '缺取消 / 重开 / 打印按钮');
    assert.match(cancelTag, /v-if="isWorking\(record\.status\)"/);
    assert.match(reopenTag, /v-if="record\.status === 'COMPLETED'"/);
    assert.match(printTag, /SCM_SORTING_PRINTABLE_STATUS\.includes\(record\.status\)/);
    // 取消与重开的原因是必填项（与后端 requireReason 同口径）
    assert.match(taskList, /if \(actionMode\.value !== 'assign' && !reason\)/);
    // 编辑权 = 干活的状态 + 派给本人 + 明细编辑权，缺一即只读
    assert.match(taskList, /isWorking\(detail\.value\.task\.status\)/);
    assert.match(taskList, /String\(assignee\) === String\(userStore\.employeeId\)/);
    assert.match(taskList, /hasPermission\(SCM_SORTING_PERMISSION\.ITEM_UPDATE\)/);
    // 未指派队列与按人筛选只对队列管理者渲染
    assert.match(taskList, /v-if="isQueueManager"[\s\S]{0,400}?unassignedOnly/);
});

test('汇总页的入口权限挂在查询按钮上，重置按钮不带权限', () => {
    assert.match(summary, /v-privilege="'scm:sorting:summary:query'"[^>]*@click="onSearch"/);
    const reset = summary.match(/<a-button([^>]*)@click="resetQuery"/);
    assert.ok(reset, '缺重置按钮');
    assert.ok(!/v-privilege/.test(reset[1]), '重置按钮不应带权限');
});

test('表格 DOM id 不重复（Playwright 定位用）', () => {
    const values = Object.values(SCM_SORTING_TABLE_ID);
    assert.ok(values.length >= 4, 'DOM id 样本太少');
    assert.equal(new Set(values).size, values.length, `存在重复 DOM id：${values}`);
    for (const id of values) {
        assert.ok(/^scm-sorting-[a-z-]+$/.test(id), `DOM id 形状不符：${id}`);
    }
});

test('列表分页上限受后端约束（pageSize > 100 会被 30001 拒绝）', () => {
    for (const [name, source] of [['任务页', taskList], ['汇总页', summary]]) {
        const options = source.match(/:page-size-options="(\[[^"]*\])"/);
        assert.ok(options, `${name} 缺 page-size-options`);
        const sizes = JSON.parse(options[1].replace(/'/g, '"')).map((v) => Number(v));
        assert.ok(Math.max(...sizes) <= 100, `${name} 的 pageSize 选项超过后端上限 100`);
    }
    assert.match(taskList, /salesOrderItemIds: \[\.\.\.candidateSelected\.value\]/);
});
