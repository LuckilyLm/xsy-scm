/**
 * 客户 360° 版式契约单测（客户列表小幅优化 + 客户详情重构）。
 *
 * 本轮把详情页从「一长串 bordered descriptions」改成「页头上下文 + 经营概览 + 业务 Card」，
 * 并把列表里的金额、编码收口到统一 formatter 与 `.scm-mono`。
 * 这类改动最容易在后续迭代里被悄悄改回去（视觉回归不报错、单测也不报错），因此钉住：
 *
 * 1. 列表列结构不得回退：不恢复「客户编码」独立列，不把「上级集团 / 更新时间」摊回列表；
 *    授信额度走 `formatAmountOrDash` + `.scm-money`。
 * 2. 详情页客户名必须在 Tabs 之上（切 Tab 后始终可见），且页头提供返回 / 编辑 / 刷新 / 更多。
 * 3. 详情页不得回退到大面积 `a-descriptions bordered`：基础资料走 label/value 字段结构。
 * 4. 五个 Tab 与 lazy load 口径不变（由 w7-customer-360 覆盖取数口径，这里只钉展示层）。
 * 5. 枚举不得直接摊给用户：商品规格状态必须走翻译 + ScmStatusTag，订单状态走 ScmStatusTag。
 * 6. 常购窗口只有 30/90/180/365 四档，用 segmented 而非 Select。
 * 7. 客户 / 供应商列表的复合单元保持双行层级：主行 14px/500/20px、副行 12px/16px，
 *    编码走 `.scm-mono` 而非胶囊标签，也不恢复独立列；列宽与 `scroll.x` 必须同步。
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

const LIST = code('../src/views/business/scm/customer/customer-list.vue');
const DETAIL = code('../src/views/business/scm/customer/customer-detail.vue');
const SUPPLIER = code('../src/views/business/scm/supplier/supplier-list.vue');
const THEME = code('../src/theme/scm/table.less');
const DETAIL_THEME = code('../src/theme/scm/detail.less');

test('列表列结构保持 8 列，不恢复客户编码独立列与主数据时间列', () => {
  const titles = [...LIST.matchAll(/\{\s*title:\s*'([^']+)',\s*dataIndex:\s*'([^']+)'/g)]
      .map((m) => m[1]);
  assert.deepEqual(
      titles,
      ['客户名称', '客户类型', '业务员', '联系方式', '结算方式', '授信额度', '状态', '操作'],
      '客户列表列结构被改动'
  );
  // 客户编码折在名称下方，不是独立列
  assert.doesNotMatch(LIST, /title:\s*'客户编码'/);
  assert.doesNotMatch(LIST, /title:\s*'上级集团'/);
  assert.doesNotMatch(LIST, /title:\s*'更新时间'/);
});

test('列表金额走统一 formatter，客户编码下沉为副行等宽文本', () => {
  // 授信额度：千分位 + ¥ + 当前业务精度（formatAmountOrDash 内部调 formatAmount）
  assert.match(LIST, /dataIndex === 'creditLimit'[^>]*class="scm-money">\{\{\s*formatAmountOrDash\(record\.creditLimit\)\s*\}\}/);
  // 客户编码折在名称下方、等宽字形；不做胶囊标签，也不恢复独立列
  assert.match(LIST, /class="scm-cell-stack__sub scm-mono">\{\{\s*record\.customerCode\s*\}\}/);
  assert.doesNotMatch(LIST, /<a-(tag|badge)[\s\S]{0,120}?record\.customerCode/);
  // 操作列仍是 编辑 / 状态 / 更多，宽度不扩大
  assert.match(LIST, /title:\s*'操作',\s*dataIndex:\s*'action',\s*width:\s*150,\s*align:\s*'center'/);
  assert.match(LIST, /<ScmActionMore/);
});

test('.scm-mono 由主题提供，只改字形不改字号', () => {
  assert.match(THEME, /\.scm-mono\s*\{[\s\S]*?font-family:\s*ui-monospace/);
  // 不得写死字号：同一个类要同时服务 12px 的副行与 14px 的表格主文本
  const block = THEME.slice(THEME.indexOf('.scm-mono'));
  assert.doesNotMatch(block.slice(0, block.indexOf('}')), /font-size/);
});

test('名称入口走公共类 .scm-cell-link，且不与 antd 同特异性打平', () => {
  // 名称入口必须复用公共类：曾经写在页面局部（.name-link），只有客户列表吃到修复，
  // 供应商列表等同类写法继续「名称居中、编码靠左」。
  assert.match(LIST, /class="scm-cell-link"/);
  assert.match(SUPPLIER, /class="scm-cell-link"/);
  assert.doesNotMatch(LIST, /class="name-link"/);
  assert.doesNotMatch(LIST, /\.name-link\s*\{/);

  // 类名写两遍（0,2,0）：antd 的链接按钮样式是 `:where(.css-xxx).ant-btn-link`，
  // `:where` 零特异性 → 实得 (0,1,0)，且它在运行时注入、排在构建产物之后，打平就输。
  // 不靠父元素提特异性，这样名称入口放进任何容器都不会退回 antd 的链接绿。
  const linkStart = THEME.indexOf('.scm-cell-link.scm-cell-link {');
  assert.ok(linkStart >= 0, '主题缺少 .scm-cell-link.scm-cell-link 规则');
  const linkDecls = THEME.slice(linkStart, THEME.indexOf('}', linkStart));
  // antd 给链接按钮加了 text-align:center，不覆盖就会在格里居中
  assert.match(linkDecls, /text-align:\s*left/);
  assert.match(linkDecls, /color:\s*var\(--scm-text/);
  // 省略号规则不能丢：长客户名要能截断，而不是顶破单元格
  assert.match(linkDecls, /text-overflow:\s*ellipsis/);
  assert.match(linkDecls, /white-space:\s*nowrap/);

  // 悬停 / 聚焦才给链接反馈（默认态是一行普通深色文本，不与状态标签抢层级）
  assert.match(
      THEME,
      /\.scm-cell-link\.scm-cell-link:hover,\s*\.scm-cell-link\.scm-cell-link:focus\s*\{[\s\S]{0,120}?color:\s*var\(--scm-primary\)/
  );

  // 容器本身不能改成 align-items: flex-start —— 那样主副行会退化成 max-content 宽度，
  // 两个 __main/__sub 的 text-overflow: ellipsis 就永远不触发。
  const stackStart = THEME.indexOf('.scm-cell-stack {');
  const stackDecls = THEME.slice(stackStart, THEME.indexOf('}', stackStart));
  assert.doesNotMatch(stackDecls, /align-items/);
});

test('双行复合单元有明确层级，不退回单行也不退回胶囊标签', () => {
  // 容器：纵向两行 + 居中 + 撑住最小高度（副行可能缺失，如没登记电话的联系人）
  const stackStart = THEME.indexOf('.scm-cell-stack {');
  assert.ok(stackStart >= 0, '主题缺少 .scm-cell-stack 规则');
  const stackDecls = THEME.slice(stackStart, THEME.indexOf('}', stackStart));
  assert.match(stackDecls, /flex-direction:\s*column/);
  assert.match(stackDecls, /justify-content:\s*center/);
  assert.match(stackDecls, /gap:\s*3px/);
  assert.match(stackDecls, /min-height:\s*38px/);

  // 主行 14px / 500 / 20px；副行 12px / 16px —— 只靠颜色区分在投屏上会糊成一片
  const mainSrc = THEME.slice(THEME.indexOf('.scm-cell-stack__main {'));
  const mainDecls = mainSrc.slice(0, mainSrc.indexOf('}'));
  assert.match(mainDecls, /font-size:\s*14px/);
  assert.match(mainDecls, /font-weight:\s*500/);
  assert.match(mainDecls, /line-height:\s*20px/);

  const subSrc = THEME.slice(THEME.indexOf('.scm-cell-stack__sub {'));
  const subDecls = subSrc.slice(0, subSrc.indexOf('}'));
  assert.match(subDecls, /font-size:\s*12px/);
  assert.match(subDecls, /line-height:\s*16px/);
  // 电话这类纯数字副行要对齐位数
  assert.match(THEME, /\.scm-cell-stack__sub--num\s*\{[\s\S]{0,60}?font-variant-numeric:\s*tabular-nums/);

  // 客户与供应商列表都用双行复合单元，联系方式都走「联系人主行 + 电话副行」
  assert.match(LIST, /<div class="scm-cell-stack">/);
  assert.match(SUPPLIER, /<div class="scm-cell-stack">/);
  assert.match(LIST, /scm-cell-stack__sub scm-cell-stack__sub--num/);
  assert.match(SUPPLIER, /scm-cell-stack__sub scm-cell-stack__sub--num/);
  // 不得退回横向单行容器
  assert.doesNotMatch(THEME, /\.scm-cell-inline/);
  assert.doesNotMatch(LIST, /class="scm-cell-inline/);
  assert.doesNotMatch(SUPPLIER, /class="scm-cell-inline/);
});

test('列宽收紧后 scroll.x 仍等于各列 width 之和', () => {
  const widths = [...LIST.matchAll(/\{\s*title:\s*'[^']+',\s*dataIndex:\s*'[^']+',\s*width:\s*(\d+)/g)]
      .map((m) => Number(m[1]));
  assert.equal(widths.length, 8, '客户列表列数被改动');
  const scrollX = /:scroll="\{ x: (\d+) \}"/.exec(LIST);
  assert.ok(scrollX, '客户列表没有声明 scroll.x');
  // 陈旧值与列宽脱钩是这张表最常见的回归：改了列宽却忘了 scroll.x，窄屏就出现无意义横向滚动
  assert.equal(widths.reduce((a, b) => a + b, 0), Number(scrollX[1]),
      `scroll.x=${scrollX[1]} 与列宽之和 ${widths.reduce((a, b) => a + b, 0)} 不一致`);
});

test('详情页客户名与编码在 Tabs 之上，切 Tab 后始终可见', () => {
  const headerIndex = DETAIL.indexOf('scm-detail-header__title');
  const tabsIndex = DETAIL.indexOf('<a-tabs');
  assert.ok(headerIndex >= 0, '缺少页头客户名');
  assert.ok(tabsIndex >= 0, '缺少 Tabs');
  assert.ok(headerIndex < tabsIndex, '客户名必须落在 Tabs 之前');
  // 页头四件套：返回 / 编辑 / 刷新（icon）/ 更多
  assert.match(DETAIL, /客户档案/);
  assert.match(DETAIL, /v-privilege="'scm:customer:update'"[\s\S]{0,80}编辑客户/);
  assert.match(DETAIL, /<ReloadOutlined\/>/);
  assert.match(DETAIL, /<ScmActionMore\s+:actions="headerActions"/);
  // 操作日志收进「更多」，不再是页头常驻按钮
  assert.match(DETAIL, /label:\s*'操作日志'/);
  assert.doesNotMatch(DETAIL, /<a-button[^>]*>\s*操作日志\s*<\/a-button>/);
});

test('基础资料不得回退到 bordered descriptions，字段走 label/value', () => {
  assert.doesNotMatch(DETAIL, /a-descriptions/);
  assert.doesNotMatch(DETAIL, /bordered\s+size="small"\s+:column/);
  // 三张业务 Card + 概览 + 弱化系统信息
  assert.match(DETAIL, /class="scm-summary"/);
  assert.match(DETAIL, /class="scm-detail-grid"/);
  assert.match(DETAIL, /class="scm-field-list scm-field-list--3"/);
  assert.match(DETAIL, /scm-detail-card--muted/);
  // 阅读宽度限制只在基础资料容器上，表格 Tab 不受限
  assert.match(DETAIL, /<div class="scm-detail-read">/);
  assert.match(DETAIL_THEME, /\.scm-detail-read\s*\{[\s\S]*?max-width:\s*1360px/);
});

test('五个 Tab 保持且 lazy 取数口径不变', () => {
  const keys = [...DETAIL.matchAll(/<a-tab-pane key="([^"]+)"/g)].map((m) => m[1]);
  assert.deepEqual(keys, ['base', 'orders', 'frequent', 'agreement', 'visibility']);
  assert.match(DETAIL, /if\s*\(customerId\.value\s*&&\s*!loaded\.value\s*&&\s*!loading\.value\)\s*void reload\(\)/);
  assert.doesNotMatch(DETAIL, /Promise\.all\(\[/);
});

test('枚举不直接摊给用户：规格状态走翻译 + ScmStatusTag，订单状态走 ScmStatusTag', () => {
  assert.match(DETAIL, /SHELF_STATUS_ENUM\.find/);
  assert.match(DETAIL, /<ScmStatusTag[^>]*:tone="shelfStatusTone\(record\.skuStatus\)"/);
  assert.doesNotMatch(DETAIL, /<a-tag>\{\{\s*record\.skuStatus/);
  assert.match(DETAIL, /<ScmStatusTag[^>]*:tone="orderStatusTone\(record\.status\)"/);
  assert.doesNotMatch(DETAIL, /<a-tag>\{\{\s*SCM_ORDER_STATUS_ENUM/);
});

test('常购窗口用 segmented 四档，协议价合并有效期，可售商品 4 列', () => {
  assert.match(DETAIL, /<a-segmented v-model:value="freqDays"/);
  assert.doesNotMatch(DETAIL, /统计窗口[\s\S]{0,200}?<a-select/);
  const freqOptions = [...DETAIL.matchAll(/\{value:\s*(\d+),\s*label:\s*'([^']+)'\}/g)].map((m) => Number(m[1]));
  assert.deepEqual(freqOptions, [30, 90, 180, 365]);

  // 只在 frequentCols 块内取列（商品 / 商品规格在协议价与可售商品里也有）
  const freqBlock = DETAIL.slice(DETAIL.indexOf('const frequentCols'), DETAIL.indexOf('const agreementCols'));
  const frequentTitles = [...freqBlock.matchAll(/\{\s*title:\s*'([^']+)'/g)].map((m) => m[1]);
  assert.deepEqual(frequentTitles, ['商品', '商品规格', '单位', '订购次数', '订购量', '最近成交价', '最近购买']);

  assert.match(DETAIL, /\{\s*title:\s*'有效期',\s*dataIndex:\s*'effectivePeriod'/);
  assert.match(DETAIL, /长期有效/);
  assert.doesNotMatch(DETAIL, /title:\s*'生效时间'/);
  assert.doesNotMatch(DETAIL, /title:\s*'结束时间'/);

  const visBlock = DETAIL.slice(DETAIL.indexOf('const visibilityCols'));
  const visTitles = [...visBlock.matchAll(/\{\s*title:\s*'([^']+)'/g)].map((m) => m[1]);
  assert.deepEqual(visTitles, ['商品', '商品规格', '状态', '加入时间']);

  assert.doesNotMatch(DETAIL, /title:\s*'商品规格编码'/);
  assert.doesNotMatch(DETAIL, /title:\s*'商品规格状态'/);
});
