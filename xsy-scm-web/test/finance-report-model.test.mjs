/**
 * Finance R0 报表中心的纯函数单测（`report-model.ts`）。
 *
 * 钉的是五个报表页共用、又最容易在改页面时被悄悄破坏的几条：
 * 1. **快捷日期**：昨日 / 本周 / 上周 / 本月 / 上月的边界，周以**周一**为起点，
 *    「本周 / 本月」**截至今天**（截至周末 / 月末会把未来日子算进已发生）；
 * 2. **366 天跨度上限**（计划 §28）与空区间的处理；
 * 3. **deep-link 还原**：URL 是用户可手改的输入，半截或非法区间必须整体作废；
 * 4. **查询装配**：清空的下拉不能以 `''` 发给后端（`@Pattern` 会拒整次查询），
 *    导出请求不带分页，日期区间不被共享筛选盖掉；
 * 5. **Tab 隔离**：切 Tab 只归目标 Tab 的页码，其它 Tab 的状态与对象身份都不变；
 * 6. **0 与 null 不合并**（A18 同族）：`null` → `—`，`"0.0000"` → `0.0000`，
 *    图表取数时 `null` 保持 `null` 而不是塌成 0；
 * 7. **成本门禁**：无成本权限时金额再「真」也不能显示，列要被整列裁掉；
 * 8. **方向由枚举派生**：入 / 出看调用方传入的集合，不在报表侧另判字符串。
 *
 * 加载方式与同目录其它测试一致：`node --experimental-strip-types` 直接吃 `.ts`，
 * 被测模块内只允许 type-only 的相对导入。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {
    buildReportQuery,
    chartValue,
    costText,
    countText,
    createTabView,
    datePresets,
    dayRange,
    defaultDateRange,
    directionText,
    enterTab,
    enumDescText,
    filterCostColumns,
    incompleteCostHint,
    movementDirection,
    optionLabel,
    presetRange,
    rangeFromQuery,
    rangeOverLimitError,
    textOrDash,
    yesNoText,
} from '../src/views/business/scm/report/report-model.ts';

/** 2026-09-23 是星期三：本周周一是 09-21，上周一 09-14、上周日 09-20。 */
const WEDNESDAY = '2026-09-23';

// ------------------------------------------------------------------
// 快捷日期
// ------------------------------------------------------------------

test('快捷日期五个预设的边界正确，周以周一为起点', () => {
    assert.deepEqual(presetRange('YESTERDAY', WEDNESDAY), ['2026-09-22', '2026-09-22']);
    assert.deepEqual(presetRange('THIS_WEEK', WEDNESDAY), ['2026-09-21', '2026-09-23']);
    assert.deepEqual(presetRange('LAST_WEEK', WEDNESDAY), ['2026-09-14', '2026-09-20']);
    assert.deepEqual(presetRange('THIS_MONTH', WEDNESDAY), ['2026-09-01', '2026-09-23']);
    assert.deepEqual(presetRange('LAST_MONTH', WEDNESDAY), ['2026-08-01', '2026-08-31']);
});

test('周日也落在它所在的那一周（周一为起点，不跳到下一周）', () => {
    // 2026-09-27 是星期日
    assert.deepEqual(presetRange('THIS_WEEK', '2026-09-27'), ['2026-09-21', '2026-09-27']);
    assert.deepEqual(presetRange('LAST_WEEK', '2026-09-27'), ['2026-09-14', '2026-09-20']);
});

test('「本周 / 本月」截至今天，不含未来日子', () => {
    // 未来日子进区间 = 趋势图上凭空多一段零值尾巴，会被读成业绩掉了
    const thisWeek = presetRange('THIS_WEEK', WEDNESDAY);
    assert.equal(thisWeek[1], WEDNESDAY);
    const thisMonth = presetRange('THIS_MONTH', WEDNESDAY);
    assert.equal(thisMonth[1], WEDNESDAY);
    // 「上周」是完整周：周一到周日
    assert.deepEqual(presetRange('LAST_WEEK', WEDNESDAY), ['2026-09-14', '2026-09-20']);
});

test('默认区间是本月，且与预设集合里的同一条完全一致', () => {
    assert.deepEqual(defaultDateRange(WEDNESDAY), presetRange('THIS_MONTH', WEDNESDAY));
});

test('预设标签齐全且顺序固定，日期选择器与文案不会两处各写一份', () => {
    const presets = datePresets(WEDNESDAY);
    assert.deepEqual(
        presets.map((item) => item.label),
        ['昨日', '本周', '上周', '本月', '上月']
    );
    for (const preset of presets) {
        assert.match(preset.value[0], /^\d{4}-\d{2}-\d{2}$/);
        assert.match(preset.value[1], /^\d{4}-\d{2}-\d{2}$/);
        assert.ok(preset.value[0] <= preset.value[1], `${preset.label} 起止倒挂`);
    }
});

// ------------------------------------------------------------------
// 跨度上限
// ------------------------------------------------------------------

test('366 天整放行，367 天拦下并说清改哪里', () => {
    assert.equal(rangeOverLimitError(['2026-09-23', '2027-09-23']), '');
    const message = rangeOverLimitError(['2026-09-23', '2027-09-24']);
    assert.match(message, /366/);
    assert.match(message, /367/);
});

test('区间缺失时不在前端报错，交给后端的日期必填', () => {
    assert.equal(rangeOverLimitError(undefined), '');
    assert.equal(rangeOverLimitError(null), '');
    assert.equal(rangeOverLimitError(['2026-09-01', '']), '');
});

// ------------------------------------------------------------------
// deep-link
// ------------------------------------------------------------------

test('deep-link 只接受两个完整的 yyyy-MM-dd，其余一律按未提供', () => {
    assert.deepEqual(rangeFromQuery({startDate: '2026-09-01', endDate: '2026-09-30'}), ['2026-09-01', '2026-09-30']);
    // 半截区间：目标页会因此回落到本月，而不是带着脏值去查
    assert.equal(rangeFromQuery({startDate: '2026-09-01'}), undefined);
    assert.equal(rangeFromQuery({endDate: '2026-09-30'}), undefined);
    assert.equal(rangeFromQuery({startDate: '2026-9-1', endDate: '2026-09-30'}), undefined);
    assert.equal(rangeFromQuery({startDate: '<script>', endDate: '2026-09-30'}), undefined);
    assert.equal(rangeFromQuery(undefined), undefined);
    // `?k=a&k=b` 取第一个值，与站内其它 deep-link 同一规则
    assert.deepEqual(rangeFromQuery({startDate: ['2026-09-01', '2026-09-02'], endDate: '2026-09-30'}), [
        '2026-09-01',
        '2026-09-30',
    ]);
});

test('某日区间两端同值；非法日回落默认区间而不是空参数', () => {
    assert.deepEqual(dayRange('2026-09-15'), ['2026-09-15', '2026-09-15']);
    assert.deepEqual(dayRange('bad-date'), [defaultDateRange()[0], defaultDateRange()[0]]);
});

// ------------------------------------------------------------------
// 查询装配
// ------------------------------------------------------------------

test('空串与 null 的筛选项被省略，不把空字符串发给后端', () => {
    const query = buildReportQuery(
        ['2026-09-01', '2026-09-30'],
        {keyword: '', movementType: null, warehouseId: undefined, supplierId: 7},
        {pageNum: 2, pageSize: 50}
    );
    assert.deepEqual(query, {supplierId: 7, startDate: '2026-09-01', endDate: '2026-09-30', pageNum: 2, pageSize: 50});
    assert.ok(!('keyword' in query), '空关键字必须省略');
    assert.ok(!('movementType' in query), '空枚举必须省略');
});

test('导出请求不带分页；带 tab 时才带分页', () => {
    const exportOnly = buildReportQuery(['2026-09-01', '2026-09-30'], {keyword: 'x'});
    assert.ok(!('pageNum' in exportOnly) && !('pageSize' in exportOnly), '导出不该带分页参数');
    const paged = buildReportQuery(['2026-09-01', '2026-09-30'], {keyword: 'x'}, {pageNum: 1, pageSize: 20});
    assert.equal(paged.pageNum, 1);
});

test('日期区间不被共享筛选里的同名字段盖掉', () => {
    // 页面类型上 shared 不含日期，但这条不变量值得钉住：口径不能由一个下拉决定
    const query = buildReportQuery(['2026-09-01', '2026-09-30'], {startDate: '2020-01-01', endDate: '2020-01-02'});
    assert.deepEqual([query.startDate, query.endDate], ['2026-09-01', '2026-09-30']);
});

test('区间不完整时不发送半截日期', () => {
    const query = buildReportQuery(['2026-09-01', ''], {keyword: 'x'});
    assert.ok(!('startDate' in query) && !('endDate' in query));
});

// ------------------------------------------------------------------
// Tab 隔离
// ------------------------------------------------------------------

test('每个 Tab 一份状态：归页码只影响目标 Tab', () => {
    const product = createTabView();
    const category = createTabView();
    product.pageNum = 3;
    category.pageNum = 5;

    enterTab(product);

    assert.equal(product.pageNum, 1, '目标 Tab 必须回到第 1 页');
    assert.equal(category.pageNum, 5, '其它 Tab 不能被连带重置（否则就是串状态）');
    assert.notEqual(product, category, '两个 Tab 必须是不同对象');
});

test('enterTab 不重置 pageSize：每页条数是用户偏好，不是查询条件', () => {
    const view = createTabView();
    view.pageSize = 100;
    view.pageNum = 4;
    enterTab(view);
    assert.equal(view.pageNum, 1);
    assert.equal(view.pageSize, 100);
});

test('Tab 状态自带空数组与零总数，模板不必判空', () => {
    const view = createTabView();
    assert.deepEqual(view.rows, []);
    assert.equal(view.total, 0);
    assert.equal(view.error, '');
    assert.equal(view.loading, false);
    assert.equal(view.pageNum, 1);
});

// ------------------------------------------------------------------
// 0 与 null
// ------------------------------------------------------------------

test('定点数三态：null 是 —，"0.0000" 原样是 0.0000', () => {
    assert.equal(textOrDash(null), '—');
    assert.equal(textOrDash(undefined), '—');
    assert.equal(textOrDash(''), '—');
    assert.equal(textOrDash('0.0000'), '0.0000');
    assert.equal(textOrDash('1234.5600'), '1234.5600');
});

test('计数三态：0 笔要显示 0，没有事实才显示 —', () => {
    assert.equal(countText(0), '0');
    assert.equal(countText(null), '—');
    assert.equal(countText(undefined), '—');
    assert.equal(countText(128), '128');
});

test('图表取数保留 null，不把它塌成 0', () => {
    assert.equal(chartValue(null), null);
    assert.equal(chartValue(''), null);
    assert.equal(chartValue('abc'), null, '脏数据不该画成零值');
    assert.equal(chartValue('0.0000'), 0);
    assert.equal(chartValue('1234.5600'), 1234.56);
});

test('成本门禁：无权限时连 0 都不显示', () => {
    assert.equal(costText('88.0000', false), '—');
    assert.equal(costText('0.0000', false), '—');
    assert.equal(costText('88.0000', true), '88.0000');
    assert.equal(costText(null, true), '—');
});

test('无成本权限时裁掉成本列，有权限时原样返回同一数组', () => {
    const columns = [{dataIndex: 'warehouseName'}, {dataIndex: 'unitCost'}, {dataIndex: 'costAmount'}, {dataIndex: 'quantity'}];
    const hidden = filterCostColumns(columns, ['unitCost', 'costAmount'], false);
    assert.deepEqual(
        hidden.map((column) => column.dataIndex),
        ['warehouseName', 'quantity']
    );
    assert.equal(filterCostColumns(columns, ['unitCost'], true), columns);
});

test('布尔事实三态：未知不等于否', () => {
    assert.equal(yesNoText(true), '是');
    assert.equal(yesNoText(false), '否');
    assert.equal(yesNoText(null), '—');
    assert.equal(yesNoText(undefined), '—');
});

// ------------------------------------------------------------------
// 不完整成本提示
// ------------------------------------------------------------------

test('跳过无成本行时必须显性提示，0 与缺失则不提示', () => {
    assert.equal(incompleteCostHint(3, '采购入库成本金额'), '采购入库成本金额已跳过 3 行无成本流水，不是全部采购入库成本金额');
    assert.equal(incompleteCostHint(0, '采购入库成本金额'), '');
    assert.equal(incompleteCostHint(null, '采购入库成本金额'), '');
});

// ------------------------------------------------------------------
// 枚举与方向
// ------------------------------------------------------------------

test('枚举文案命不中时回落原值，不把未知类型藏成 —', () => {
    const labels = {PURCHASE_IN: {value: 'PURCHASE_IN', desc: '采购入库'}};
    assert.equal(enumDescText('PURCHASE_IN', labels), '采购入库');
    assert.equal(enumDescText('FUTURE_TYPE', labels), 'FUTURE_TYPE');
    assert.equal(enumDescText(null, labels), '—');
    assert.deepEqual(optionLabel([{value: 'STANDARD', label: '标品'}], 'STANDARD'), '标品');
    assert.equal(optionLabel([{value: 'STANDARD', label: '标品'}], 'OTHER'), 'OTHER');
});

test('方向只由传入的入方向集合派生，报表侧不再判字符串', () => {
    const inbound = ['PURCHASE_IN', 'STOCKTAKE_GAIN', 'GAIN_REPORT', 'TRANSFER_IN', 'CONVERT_IN'];
    assert.equal(movementDirection('PURCHASE_IN', inbound), 'IN');
    assert.equal(movementDirection('SALES_OUT', inbound), 'OUT');
    assert.equal(movementDirection('LOSS_REPORT', inbound), 'OUT');
    assert.equal(movementDirection('STOCKTAKE_GAIN', inbound), 'IN');
    assert.equal(movementDirection(null, inbound), null);
    assert.equal(directionText('IN'), '入');
    assert.equal(directionText('OUT'), '出');
    assert.equal(directionText(null), '—');
});
