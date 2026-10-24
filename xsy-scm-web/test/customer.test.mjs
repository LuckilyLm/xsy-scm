/**
 * 客户模块契约测试。
 *
 * 由 5 个独立测试文件按模块合并：每个 `{ … }` 块对应原来一份文件，
 * 块内 helper / 常量各自作用域隔离；只有 import 提到顶部并去重。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
  toCustomerPayload,
  validateCustomer,
  emptyCustomer,
  applyCreditPeriodType,
  applyCreditPeriodUnit,
} from '../src/views/business/scm/customer/customer-form-model.ts';
import {QUERY_FILTER_KEY_PREFIX, queryFilterStorageKey} from '../src/lib/query-filter-key.ts';

// ---------------------------------------------------------------- 来源：customer-360-layout-contract.test.mjs
{
  /**
   * 客户 360° 版式契约单测（客户列表小幅优化 + 客户详情重构）。
   *
   * 本轮把详情页从「一长串 bordered descriptions」改成「页头上下文 + 经营概览 + 业务 Card」，
   * 并把列表里的金额、编码收口到统一 formatter 与 `.scm-mono`。
   * 这类改动最容易在后续迭代里被悄悄改回去（视觉回归不报错、单测也不报错），因此钉住：
   *
   * 1. 客户列表把名称、编码、联系人和电话分列，客户编码可由 TableOperator 打开；
   *    不把「上级集团 / 更新时间」摊回列表，授信额度走 `formatAmountOrDash` + `.scm-money`。
   * 2. 详情页客户名必须在 Tabs 之上（切 Tab 后始终可见），页头只保留返回入口。
   * 3. 详情页不得回退到大面积 `a-descriptions bordered`：基础资料走 label/value 字段结构。
   * 4. 基础五个 Tab 与客户 SKU 别名 Tab 共六个，仍由各 Tab lazy 取数。
   * 5. 枚举不得直接摊给用户：商品规格状态必须走翻译 + ScmStatusTag，订单状态走 ScmStatusTag。
   * 6. 常购窗口只有 30/90/180/365 四档，用 segmented 而非 Select。
   * 7. 客户与供应商列表在桌面把名称、编码、联系人与电话分列；编码走 `.scm-mono`，
   *    低频编码可默认收起，列宽与 `scroll.x` 必须同步。
   */

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

  test('客户列表将独立识别字段分列，且不摊开主数据时间列', () => {
    const titles = [...LIST.matchAll(/\{\s*title:\s*'([^']+)',\s*dataIndex:\s*'([^']+)'/g)].map((m) => m[1]);
    assert.deepEqual(
      titles,
      ['客户名称', '客户编码', '客户类型', '业务员', '联系人', '联系电话', '结算方式', '授信额度', '状态', '操作'],
      '客户列表列结构被改动'
    );
    assert.match(LIST, /title: '客户编码', dataIndex: 'customerCode', width: 160, showFlag: false/);
    assert.doesNotMatch(LIST, /title:\s*'上级集团'/);
    assert.doesNotMatch(LIST, /title:\s*'更新时间'/);
  });

  test('列表金额走统一 formatter，客户编码独立列默认收起', () => {
    // 授信额度：千分位 + ¥ + 当前业务精度（formatAmountOrDash 内部调 formatAmount）
    assert.match(LIST, /dataIndex === 'creditLimit'[^>]*class="scm-money">\{\{\s*formatAmountOrDash\(record\.creditLimit\)\s*\}\}/);
    // 客户编码有独立列，默认收起；显示时使用等宽字形，不做胶囊标签。
    assert.match(LIST, /dataIndex === 'customerCode'[^>]*class="scm-mono">\{\{\s*record\.customerCode/);
    assert.match(LIST, /dataIndex === 'contactPhone'[^>]*class="scm-mono">\{\{\s*record\.contactPhone/);
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
    // 能点进详情的名称入口默认就用主题色（跟随「网站设置」里的主题色），
    // 不用先悬停才变色。
    assert.match(linkDecls, /color:\s*var\(--scm-primary\)/);
    // 省略号规则不能丢：长客户名要能截断，而不是顶破单元格
    assert.match(linkDecls, /text-overflow:\s*ellipsis/);
    assert.match(linkDecls, /white-space:\s*nowrap/);

    // 悬停 / 聚焦再补下划线，强化可点反馈
    assert.match(
      THEME,
      /\.scm-cell-link\.scm-cell-link:hover,\s*\.scm-cell-link\.scm-cell-link:focus\s*\{[\s\S]{0,120}?color:\s*var\(--scm-primary\)/
    );

    // 复合单元已整体退役：主题里不得再有 .scm-cell-stack 及其 __main/__sub 规则。
    assert.doesNotMatch(THEME, /\.scm-cell-stack/);
  });

  test('客户与供应商的独立字段各自成列，不再叠进同一个单元格', () => {
    // 客户和供应商名称、编码、联系人和电话应各有数据列
    for (const title of ['供应商名称', '供应商编码', '联系人', '联系电话']) {
      assert.match(SUPPLIER, new RegExp(`title:\\s*'${title}'`));
    }
    for (const title of ['客户名称', '客户编码', '联系人', '联系电话']) {
      assert.match(LIST, new RegExp(`title:\\s*'${title}'`));
    }
    assert.doesNotMatch(LIST, /class="scm-cell-stack/);
    assert.doesNotMatch(SUPPLIER, /class="scm-cell-stack/);
    assert.match(SUPPLIER, /dataIndex === 'contactPhone'[\s\S]{0,120}class="scm-mono"/);
    // 不得引入只靠横向单行容器替代层级信息的旧样式
    assert.doesNotMatch(THEME, /\.scm-cell-inline/);
    assert.doesNotMatch(LIST, /class="scm-cell-inline/);
    assert.doesNotMatch(SUPPLIER, /class="scm-cell-inline/);
  });

  test('滚动宽度跟随当前可见列，隐藏列不占空间', () => {
    const columns = [...LIST.matchAll(/\{\s*title:\s*'[^']+',\s*dataIndex:\s*'[^']+',\s*width:\s*(\d+)([^}]*)\}/g)].map((m) => ({
      width: Number(m[1]),
      hiddenByDefault: /showFlag:\s*false/.test(m[2]),
    }));
    assert.equal(columns.length, 10, '客户列表列数被改动');
    const visibleWidth = columns.filter((column) => !column.hiddenByDefault).reduce((sum, column) => sum + column.width, 0);
    assert.ok(visibleWidth > 0);
    // 列宽与铺满统一走 useScmTableFill：scrollX 由它算出
    assert.match(LIST, /:scroll="\{\s*x:\s*scrollX\s*\}"/);
    assert.match(LIST, /useScmTableFill\(columns\)/);
    assert.match(LIST, /<TableOperator v-model="columns"/);

    const supplierWidths = [...SUPPLIER.matchAll(/\{title:\s*'[^']+',\s*dataIndex:\s*'[^']+',\s*width:\s*(\d+)/g)].map((m) => Number(m[1]));
    assert.ok(supplierWidths.reduce((a, b) => a + b, 0) > 0);
    assert.match(SUPPLIER, /:scroll="\{\s*x:\s*scrollX\s*\}"/);
    assert.match(SUPPLIER, /useScmTableFill\(columns\)/);
  });

  test('详情页客户名与编码在 Tabs 之上，切 Tab 后始终可见', () => {
    const headerIndex = DETAIL.indexOf('scm-detail-header__title');
    const tabsIndex = DETAIL.indexOf('<a-tabs');
    assert.ok(headerIndex >= 0, '缺少页头客户名');
    assert.ok(tabsIndex >= 0, '缺少 Tabs');
    assert.ok(headerIndex < tabsIndex, '客户名必须落在 Tabs 之前');
    // 详情页只保留返回；编辑由列表承担，失败重试留在对应内容区。
    assert.match(DETAIL, /客户档案/);
    assert.doesNotMatch(DETAIL, /<ReloadOutlined\/>|<ScmActionMore|openEditDrawer|openOperateLog/);
    assert.match(DETAIL, /<a-button size="small" @click="loadBase">重新加载<\/a-button>/);
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

  test('客户资料与别名共六个 Tab，lazy 取数口径不变', () => {
    const keys = [...DETAIL.matchAll(/<a-tab-pane key="([^"]+)"/g)].map((m) => m[1]);
    assert.deepEqual(keys, ['base', 'orders', 'frequent', 'agreement', 'visibility', 'aliases']);
    assert.match(DETAIL, /key="aliases"/);
    assert.match(DETAIL, /CustomerSkuAlias/);
    assert.match(DETAIL, /if\s*\(customerId\.value\s*&&\s*!loaded\.value\s*&&\s*!loading\.value\)\s*void reload\(\)/);
    assert.doesNotMatch(DETAIL, /Promise\.all\(\[/);
  });

  test('枚举不直接摊给用户：规格状态走翻译 + ScmStatusTag，订单状态走 ScmStatusTag', () => {
    assert.match(DETAIL, /shelfStatusLabel\(record\.skuStatus\)/);
    assert.match(DETAIL, /import \{shelfStatusLabel\} from '\/@\/constants\/business\/scm\/product-const'/);
    assert.match(DETAIL, /value === 'ON_SHELF'[\s\S]{0,100}\? 'success'[\s\S]{0,100}: 'warning'/);
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
    assert.deepEqual(frequentTitles, ['商品', '商品规格', '商品规格编码', '单位', '订购次数', '订购量', '最近成交价', '最近购买']);

    assert.match(DETAIL, /\{\s*title:\s*'有效期',\s*dataIndex:\s*'effectivePeriod'/);
    assert.match(DETAIL, /长期有效/);
    assert.doesNotMatch(DETAIL, /title:\s*'生效时间'/);
    assert.doesNotMatch(DETAIL, /title:\s*'结束时间'/);

    const visBlock = DETAIL.slice(DETAIL.indexOf('const visibilityCols'));
    const visTitles = [...visBlock.matchAll(/\{\s*title:\s*'([^']+)'/g)].map((m) => m[1]);
    assert.deepEqual(visTitles, ['商品', '商品规格', '状态', '加入时间']);

    // 常购商品与协议价的「商品规格编码」都独立成列（一格一值）
    assert.match(DETAIL, /title:\s*'商品规格编码'/);
    assert.doesNotMatch(DETAIL, /title:\s*'商品规格状态'/);
  });
}

// ---------------------------------------------------------------- 来源：customer-form-model.test.mjs
{
  /*
   * 客户表单模型单测
   *
   * 运行方式与断言风格与 `test/product-form-model.test.mjs` 一致。
   * （`node --experimental-strip-types --test`，直接 import `.ts` 源码）。
   *
   * 覆盖的是后端会拒绝的非法组合，也就是前端必须提前挡住的那些：
   * 账期三形态互斥、金额格式、可空字段的 `null` 语义。
   */

  test('新建客户默认独立结算，授信额度是字符串零而不是数字零', () => {
    const form = emptyCustomer();
    assert.equal(form.settleMode, 'INDEPENDENT');
    // 关键：金额是 4 位定点字符串。如果这里变成数字 0，说明有人偷偷做了 Number() 运算。
    assert.equal(form.creditLimit, '0.0000');
    assert.equal(form.creditPeriodType, undefined);
    assert.equal(form.customerTypeId, undefined);
    // 区划六列必须由新建初值显式置 null：漏一列（undefined）就会让上一条记录的区划串进新客户。
    for (const key of ['provinceCode', 'provinceName', 'cityCode', 'cityName', 'districtCode', 'districtName']) {
      assert.equal(form[key], null, `emptyCustomer 缺少区划列 ${key}`);
    }
  });

  test('切换账期形态会清掉不属于该形态的字段，避免后端 40000', () => {
    const dirty = {
      ...emptyCustomer(),
      creditAmountThreshold: '100.0000',
      creditPeriodValue: 30,
      creditPeriodUnit: 'MONTH',
      settleDay: 15,
    };

    const byAmount = applyCreditPeriodType(dirty, 'BY_AMOUNT');
    assert.equal(byAmount.creditPeriodType, 'BY_AMOUNT');
    assert.equal(byAmount.creditAmountThreshold, '100.0000');
    assert.equal(byAmount.creditPeriodValue, undefined);
    assert.equal(byAmount.creditPeriodUnit, undefined);
    assert.equal(byAmount.settleDay, undefined);

    const byTime = applyCreditPeriodType(dirty, 'BY_TIME');
    assert.equal(byTime.creditPeriodType, 'BY_TIME');
    assert.equal(byTime.creditAmountThreshold, undefined);
    // dirty 里已经选过 MONTH，切回「按时间」时保留用户上次的选择（连带保留结算日）。
    assert.equal(byTime.creditPeriodUnit, 'MONTH');
    assert.equal(byTime.settleDay, 15);

    // 从未选过单位时默认给「天」，否则用户必须多点一次才能填账期值。
    const freshByTime = applyCreditPeriodType(emptyCustomer(), 'BY_TIME');
    assert.equal(freshByTime.creditPeriodUnit, 'DAY');
    assert.equal(freshByTime.settleDay, undefined);

    const cleared = applyCreditPeriodType({...dirty, creditPeriodType: 'BY_TIME'}, undefined);
    assert.equal(cleared.creditPeriodType, undefined);
    assert.equal(cleared.creditPeriodValue, undefined);
    assert.equal(cleared.settleDay, undefined);
  });

  test('账期单位切到「天」会清掉固定结算日', () => {
    const monthly = {
      ...emptyCustomer(),
      creditPeriodType: 'BY_TIME',
      creditPeriodValue: 1,
      creditPeriodUnit: 'MONTH',
      settleDay: 10,
    };
    const daily = applyCreditPeriodUnit(monthly, 'DAY');
    assert.equal(daily.creditPeriodUnit, 'DAY');
    assert.equal(daily.settleDay, undefined);

    const stillMonthly = applyCreditPeriodUnit(monthly, 'MONTH');
    assert.equal(stillMonthly.settleDay, 10);
  });

  test('校验必填项、电话格式与金额精度', () => {
    // 编码由服务端生成：不带编码的合法表单必须直接通过校验。
    const ok = {...emptyCustomer(), name: '客户甲', customerTypeId: 1};
    assert.equal(validateCustomer(ok), undefined);

    assert.match(validateCustomer({...ok, name: ''}), /客户名称/);
    assert.match(validateCustomer({...ok, customerTypeId: undefined}), /客户类型/);
    assert.match(validateCustomer({...ok, contactPhone: '12345'}), /联系电话/);
    assert.match(validateCustomer({...ok, creditLimit: '1.23456'}), /两位小数/);
    assert.match(validateCustomer({...ok, creditLimit: '-1'}), /两位小数/);
    // 合法的 4 位定点数必须放行。
    assert.equal(validateCustomer({...ok, creditLimit: '1234.5000'}), undefined);
    assert.equal(validateCustomer({...ok, contactPhone: '13800138000'}), undefined);
  });

  test('按金额账期必须有阈值，按时间账期必须有值且结算日限 1–28', () => {
    const ok = {...emptyCustomer(), name: '客户甲', customerTypeId: 1};

    assert.match(validateCustomer({...ok, creditPeriodType: 'BY_AMOUNT'}), /金额阈值/);
    assert.match(validateCustomer({...ok, creditPeriodType: 'BY_AMOUNT', creditAmountThreshold: '1.23456'}), /两位小数/);
    assert.equal(validateCustomer({...ok, creditPeriodType: 'BY_AMOUNT', creditAmountThreshold: '5000.0000'}), undefined);

    assert.match(validateCustomer({...ok, creditPeriodType: 'BY_TIME'}), /账期值/);
    assert.match(validateCustomer({...ok, creditPeriodType: 'BY_TIME', creditPeriodValue: 0}), /账期值/);
    // 有账期值但没单位：后端 ck_customer_credit_period 不接受，前端必须提前挡住。
    assert.match(validateCustomer({...ok, creditPeriodType: 'BY_TIME', creditPeriodValue: 30}), /账期单位/);
    assert.equal(validateCustomer({...ok, creditPeriodType: 'BY_TIME', creditPeriodValue: 30, creditPeriodUnit: 'DAY'}), undefined);

    const monthly = {...ok, creditPeriodType: 'BY_TIME', creditPeriodValue: 1, creditPeriodUnit: 'MONTH'};
    assert.equal(validateCustomer({...monthly, settleDay: 28}), undefined);
    assert.match(validateCustomer({...monthly, settleDay: 29}), /1 到 28/);
    assert.match(validateCustomer({...monthly, settleDay: 0}), /1 到 28/);
  });

  test('提交前归一化：姓名去空白、空白串转 null、零额度保持字符串', () => {
    const payload = toCustomerPayload({
      ...emptyCustomer(),
      name: '  客户甲  ',
      customerTypeId: 1,
      contactName: '   ',
      remark: ' 有备注 ',
    });

    assert.equal(payload.name, '客户甲');
    // 空白 → null：后端 FieldStrategy.ALWAYS 只对 null 生效，送 "" 会变成「想清空却清不掉」。
    assert.equal(payload.contactName, null);
    assert.equal(payload.remark, '有备注');
    assert.equal(payload.parentCustomerId, null);
    assert.equal(payload.sellerId, null);
    assert.equal(payload.supplierId, null);
    assert.equal(payload.creditPeriodType, null);
    assert.equal(payload.creditAmountThreshold, null);
    assert.equal(payload.settleDay, null);
    // "0.0000" 不是空白，必须原样保留 —— 这是「额度为零」与「未设置额度」的区别。
    assert.equal(payload.creditLimit, '0.0000');
  });

  test('写请求体不含客户编码：编码由服务端生成', () => {
    const payload = toCustomerPayload({...emptyCustomer(), customerCode: 'CUS000001', name: '客户甲'});
    // 编码只在 UI 表单模型（CustomerFormModel）上做只读回显，写请求体（CustomerPayload）没有这个键。
    assert.equal('customerCode' in payload, false);
    assert.equal(payload.name, '客户甲');
  });

  test('货到付款默认关闭，只有后台明确勾选才提交开通', () => {
    const fresh = emptyCustomer();
    assert.equal(fresh.codAllowed, false);
    assert.equal(toCustomerPayload(fresh).codAllowed, false);
    assert.equal(toCustomerPayload({...fresh, codAllowed: true}).codAllowed, true);
  });
}

// ---------------------------------------------------------------- 来源：detail-pages-sections-contract.test.mjs
{
  /**
   * 客户 / 供应商 / 订单三个详情页的分区契约测试。
   *
   * 三页原本都把「基本信息 + 账期 / 关联表」串成一长串 `a-descriptions` + `a-divider`，
   * 编码、时间与业务字段混在一起，没有分区锚点（核心业务信息优先、日志再次、系统信息最后）。
   *
   * 本文件钉住：
   * 1. 按业务 Section 分区：每页核心业务段在前、系统信息段在最后。
   * 2. 不再用 `a-divider` 当分区标题，只保留模态框内的合法分隔。
   * 3. 编码与时间下沉到系统信息段，不再出现在第一个描述列表里。
   * 4. 数值走全局 `.scm-money` / `.scm-quantity`，不留局部 `.amount` 样式。
   */

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
    assert.deepEqual(
      positions,
      [...positions].sort((a, b) => a - b),
      '客户详情分区顺序不符'
    );
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

  test('供应商详情按「联系与地址 / 采购与账期 / 已关联商品 / 系统信息」分区', () => {
    // 供应商与客户、线路共用 SCM 资料模板：身份信息进页头（名称 + 状态 + 编码），
    // 正文只留可读的业务分区，因此不再有独立的「概览」段。
    const sections = ['联系与地址', '采购与账期', '已关联商品', '系统信息'];
    const positions = sections.map((title) => posOf(supplier, title));
    assert.deepEqual(
      positions,
      [...positions].sort((a, b) => a - b),
      '供应商详情分区顺序不符'
    );
    const sysIndex = posOf(supplier, '系统信息');
    const tail = supplier.slice(sysIndex);
    // 时间、编码与备注落在系统信息段内（label/value 结构用 dt）
    assert.match(tail, />创建时间</);
    assert.match(tail, />更新时间</);
    assert.match(tail, />供应商编码</);
    assert.match(tail, />备注</);
    // 页头承担身份识别：名称、状态、编码
    assert.match(supplier, /scm-detail-header__title/);
    assert.match(supplier, /ScmStatusTag/);
  });

  test('订单详情按「摘要 / 客户配送 / 金额 / 商品 / 操作记录 / 系统信息」分区', () => {
    const sections = ['状态与核心摘要', '客户与配送', '金额结算', '商品明细', '操作记录', '系统信息'];
    const positions = sections.map((title) => posOf(order, title));
    assert.deepEqual(
      positions,
      [...positions].sort((a, b) => a - b),
      '订单详情分区顺序不符'
    );
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
    for (const [name, source] of [
      ['客户详情', customer],
      ['供应商详情', supplier],
    ]) {
      assert.ok(!/class="amount"/.test(source), `${name} 仍用局部 .amount 类`);
      assert.ok(!/\.amount\s*\{/.test(source), `${name} 仍定义局部 .amount 样式`);
    }
    assert.match(customer, /class="scm-money"/);
    assert.match(customer, /class="scm-quantity"/);
    assert.match(supplier, /class="scm-money"/);
  });
}

// ---------------------------------------------------------------- 来源：customer-360-contract.test.mjs
{
  /**
   * 客户 360° 上下文（customer-detail.vue 五 Tab）的前端契约测试。
   *
   * 钉死这一波最容易被日后改动悄悄破坏、且破坏后不报错的前端边界（后端聚合与排除口径由
   * CustomerFrequentSkuIT 覆盖）：
   * 1. 常购商品走只读 GET 聚合（getRequest + days/limit），不是新建副本表也不是写命令；
   * 2. 五个上下文视图全部复用各领域既有查询接口（customer/order/pricing/visibility），
   *    不引入第二份事实，也不在详情里落任何写操作；
   * 3. 所有 Tab 锁定同一个 customerId（深链参数即唯一上下文）；
   * 4. 常购「数量」列语义是订购量而非结算 / 实重量、以 4 位小数字符串原样展示（不按金额格式化），
   *    且行按 SKU＋单位拆分、明确不跨单位求和；最近成交价缺失即空不兜底。
   */

  /** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const API = '../src/api/business/scm/customer-api.ts';
  const DETAIL = '../src/views/business/scm/customer/customer-detail.vue';

  test('常购商品是只读 GET 聚合，带 days/limit，不新建写端点', () => {
    const api = code(API);
    assert.match(
      api,
      /frequentSkus:\s*\(customerId:\s*ScmId,\s*days\s*=\s*90,\s*limit\s*=\s*20\)\s*=>\s*getRequest(?:<[^'`]*>)?\(`\/scm\/customer\/\$\{customerId\}\/frequent-skus`,\s*\{days,\s*limit\}\)/
    );
    // 只读：绝不能用 postRequest 打到 frequent-skus
    assert.doesNotMatch(api, /postRequest\(`\/scm\/customer\/\$\{customerId\}\/frequent-skus/);
  });

  test('五个上下文视图全部复用既有查询接口，详情侧不落任何写命令', () => {
    const vue = code(DETAIL);
    assert.match(vue, /customerApi\.detail\(/);
    assert.match(vue, /orderApi\s*\.\s*query\(\{[^}]*customerId:\s*customerId\.value/);
    assert.match(vue, /customerApi\.frequentSkus\(customerId\.value/);
    assert.match(vue, /pricingApi\s*\.\s*agreement\s*\.\s*query\(\{[^}]*customerId:\s*customerId\.value/);
    assert.match(vue, /customerVisibilityApi\s*\.\s*query\(\{[^}]*customerId:\s*customerId\.value/);
    // 360° 视图纯只读：不新增 / 编辑 / 删除 / 状态变更客户
    assert.doesNotMatch(vue, /customerApi\.(add|update|updateStatus|delete)\(/);
  });

  test('所有 Tab 锁定同一深链 customerId，切换客户整体复位、Tab 首次进入才加载', () => {
    const vue = code(DETAIL);
    // 唯一上下文来源：route.query.customerId 且必须是纯数字
    assert.match(vue, /route\.query\.customerId[\s\S]*?\/\^\\d\+\$\/\.test\(id\)/);
    // 非基础 Tab 复用同一 loader：仅当未加载且不在加载中才发请求
    assert.match(vue, /if\s*\(customerId\.value\s*&&\s*!loaded\.value\s*&&\s*!loading\.value\)\s*void reload\(\)/);
    // 换客户复位所有已加载 Tab，避免串上下文
    assert.match(vue, /watch\(customerId,\s*\(\)\s*=>\s*\{[\s\S]*?resetAllTabs\(\);[\s\S]*?loadBase\(\)/);
  });

  test('常购数量列语义是订购量、原样 4 位小数字符串展示、按单位分行不跨单位求和', () => {
    const vue = code(DETAIL);
    // 列标题为「订购量」而非结算 / 实重；且以纯文本展示，不按金额格式化（不加 ¥、不二次定点）
    assert.match(vue, /\{\s*title:\s*'订购量',\s*dataIndex:\s*'orderedQuantity'/);
    assert.match(vue, /\{\{\s*record\.orderedQuantity\s*\}\}/);
    assert.doesNotMatch(vue, /formatAmount(?:OrDash)?\(record\.orderedQuantity\)/);
    // 行键含单位：同 SKU 不同历史单位分行，绝不把不同单位的量相加成一个总量
    assert.match(vue, /row-key="\(r:\s*CustomerFrequentSku\)\s*=>\s*`\$\{r\.skuId\}-\$\{r\.unit\}`"/);
    assert.match(vue, /不跨单位求和/);
    // 最近成交价缺失即空（走 null 兜底为破折号的 formatAmountOrDash），不回退草稿 / 当前价
    assert.match(vue, /formatAmountOrDash\(record\.recentUnitPrice\)/);
  });
}

// ---------------------------------------------------------------- 来源：query-filter-memory.test.mjs
{
  /**
   * 列表查询条件「按用户 + 页面」本地记忆的前端契约测试。
   *
   * 钉死这一波最容易被日后改动悄悄破坏的边界：
   * 1. 存储键纯函数：不同 employeeId / 不同 pageKey 生成不同键，且带统一前缀（用户之间不串味）；
   * 2. 客户列表页确实接入组合式：查询时保存、重置时清空、挂载时恢复并强制回到第 1 页；
   * 3. 深链详情类页面（customer-detail）绝不调用查询记忆组合式，避免旧筛选污染深链上下文。
   */

  /** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const LIST = '../src/views/business/scm/customer/customer-list.vue';
  const DETAIL = '../src/views/business/scm/customer/customer-detail.vue';

  test('存储键按用户与页面隔离，带统一前缀', () => {
    const keyA = queryFilterStorageKey('44', 'scm:customer:list');
    const keyB = queryFilterStorageKey('45', 'scm:customer:list');
    const keyC = queryFilterStorageKey('44', 'scm:order:list');
    assert.ok(keyA.startsWith(QUERY_FILTER_KEY_PREFIX + ':'), '键须带统一前缀');
    // 不同用户 → 不同键（不串味）
    assert.notEqual(keyA, keyB);
    // 同用户不同页面 → 不同键
    assert.notEqual(keyA, keyC);
    // 结构可复核：前缀:用户:页面
    assert.equal(keyA, 'xsy-scm:query-filter:44:scm:customer:list');
  });

  test('客户列表页接入查询记忆：查询保存、重置清空、挂载恢复并回到第 1 页', () => {
    const vue = code(LIST);
    assert.match(vue, /import \{useQueryFilterMemory\} from '\/@\/lib\/query-filter-memory';/);
    assert.match(vue, /const queryMemory = useQueryFilterMemory<CustomerQuery>\('scm:customer:list'\);/);
    // 查询：先回到第 1 页再落偏好，落的是当前筛选快照
    assert.match(vue, /function search\(\)\s*\{\s*filters\.pageNum = 1;\s*queryMemory\.save\(\{\.\.\.filters\}\);/);
    // 重置：清空偏好
    assert.match(vue, /function reset\(\)\s*\{[\s\S]*?queryMemory\.clear\(\);/);
    // 挂载：恢复记忆后强制 pageNum=1，不把用户带回旧页码
    assert.match(vue, /Object\.assign\(filters, queryMemory\.load\(\), \{pageNum: 1\}\);/);
    // 模板可达性：光有 search() 实现没用，按钮必须真的绑到它（历史上这里是死按钮）
    assert.match(vue, /<a-button type="primary" @click="search">查询<\/a-button>/);
  });

  test('深链详情页不接入查询记忆，避免旧筛选污染 customerId 上下文', () => {
    const vue = code(DETAIL);
    assert.doesNotMatch(vue, /useQueryFilterMemory/);
    assert.doesNotMatch(vue, /query-filter/);
  });
}
