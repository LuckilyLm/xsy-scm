/**
 * 价格模块契约测试。
 *
 * 由 2 个独立测试文件按模块合并：每个 `{ … }` 块对应原来一份文件，
 * 块内 helper / 常量各自作用域隔离；只有 import 提到顶部并去重。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {formatAmount, formatAmountOrDash} from '../src/utils/scm-amount.ts';

// ---------------------------------------------------------------- 来源：scm-price-source-contract.test.mjs
{
  /*
   * 价格来源是后端枚举直出的字段：前端只要漏一个映射，订单行就会把英文常量端给使用者。
   * 这条契约把「后端枚举 = 前端映射键」钉死，新增来源时漏改会当场失败。
   */

  const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

  const backend = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/pricing/constant/ScmPriceSourceEnum.java');
  const orderConst = read('../src/constants/business/scm/order-const.ts');
  const pricingConst = read('../src/constants/business/scm/pricing-const.ts');
  const display = read('../src/views/business/scm/pricing/pricing-display.ts');
  const preview = read('../src/views/business/scm/pricing/price-preview.vue');

  /** 从 Java 枚举体里取常量名：形如 `AGREEMENT("客户协议价"),`，取左括号前的标识符。 */
  function backendSources() {
    return [...backend.matchAll(/^\s{4}([A-Z_]+)\(/gm)].map((m) => m[1]);
  }

  test('后端价格来源枚举非空，避免契约空转', () => {
    const sources = backendSources();
    assert.ok(sources.length >= 4, `解析到的来源过少：${sources.join(',')}`);
    assert.ok(sources.includes('TIME_PRICE'));
  });

  test('订单行与定价常量都覆盖后端每一个价格来源', () => {
    for (const source of backendSources()) {
      assert.match(orderConst, new RegExp(`${source}: \\{value: '${source}'`), `订单常量缺 ${source}`);
      assert.match(pricingConst, new RegExp(`${source}: \\{value: '${source}'`), `定价常量缺 ${source}`);
    }
  });

  test('价格来源的中文文案与颜色档位覆盖每一个来源', () => {
    for (const source of backendSources()) {
      // MARKET 是兜底来源（读 SKU 自身市场价），不产生价格历史记录，历史文案不要求它
      if (source !== 'MARKET') {
        assert.match(display, new RegExp(`${source}: '`), `历史来源文案缺 ${source}`);
      }
      assert.match(preview, new RegExp(`${source}: '`), `预览页颜色档位缺 ${source}`);
    }
  });
}

// ---------------------------------------------------------------- 来源：pricing-contract.test.mjs
{
  /** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  test('amount contract keeps zero distinct from missing price', () => {
    assert.equal(formatAmount('0.0000'), '¥ 0.00');
    assert.equal(formatAmount(null), '未定价');
    assert.equal(formatAmount(undefined), '未定价');
    assert.equal(formatAmount('1234567.5'), '¥ 1,234,567.50');
    assert.equal(formatAmountOrDash(null), '—');
  });

  test('pricing status includes formula-specific missing basis reasons and five unavailable reasons', async () => {
    const constants = await import('../src/constants/business/scm/pricing-const.ts');
    assert.deepEqual(Object.keys(constants.UNPRICED_REASON_ENUM), [
      'NO_PRICE_SOURCE',
      'AGREEMENT_COST_MISSING',
      'AGREEMENT_MARKET_PRICE_MISSING',
      'AGREEMENT_RESULT_INVALID',
    ]);
    assert.deepEqual(Object.keys(constants.UNAVAILABLE_REASON_ENUM).sort(), [
      'CATEGORY_DISABLED',
      'NOT_VISIBLE',
      'SKU_NOT_FOUND',
      'SKU_OFF_SHELF',
      'SPU_OFF_SHELF',
    ]);
  });

  test('pricing error mapping preserves overlap, conflict, idempotency and sellability codes', async () => {
    const {pricingError} = await import('../src/views/business/scm/pricing/pricing-errors.ts');
    assert.match(pricingError({code: 40933}), /重叠/);
    assert.match(pricingError({code: 40935}), /重叠/);
    assert.match(pricingError({code: 40921}), /修改/);
    assert.match(pricingError({code: 40948}), /批次号/);
    assert.match(pricingError({code: 40949}), /不可售/);
  });

  // ------------------------------------------------------------------ 价格中心展示

  test('协议价抽屉：单价用 InputNumber + ¥，宽度走 foundation 分级', () => {
    const drawer = code('../src/views/business/scm/pricing/components/agreement-price-form-drawer.vue');
    assert.match(drawer, /<a-input-number[\s\S]{0,240}unitPrice/);
    assert.match(drawer, /addon-before="¥"/);
    // 销售侧单价按两位小数；加价金额两位，加价率仍保留四位
    assert.match(drawer, /v-model:value="form\.unitPriceInput"[\s\S]{0,200}:precision="2"/);
    assert.match(drawer, /v-model:value="form\.formulaParameter"[\s\S]{0,300}MARKET_PLUS_AMOUNT' \? 2 : 4/);
    assert.match(drawer, /:min="0"/);
    // 提交前必须收口回 4 位定点字符串：后端拒绝 JSON 数字，也拒绝空串
    assert.match(drawer, /form\.unitPrice\s*=\s*form\.formulaType\s*===\s*'FIXED'\s*\?\s*moneyPayload\(form\.unitPriceInput\)\s*:\s*null/);
    // 模型必须是稳定对象：`:model="{...form, ...}"` 每次渲染新建对象，change 校验读到的是上一份快照，
    // 表现为「选了客户仍提示请选择客户」「填了单价仍提示请填写单价」
    assert.doesNotMatch(drawer, /:model="\{\.\.\./);
    assert.match(drawer, /:model="form"/);
    assert.match(drawer, /form\.formulaParameter\s*=\s*form\.formulaType === 'FIXED'\s*\?\s*null/);
    assert.match(drawer, /'COST_MARKUP_RATE'/);
    assert.match(drawer, /'MARKET_MARKUP_RATE'/);
    assert.match(drawer, /moneyPayload.*from '\/@\/utils\/scm-amount'/);
    // 宽度走分级，不写死像素（foundation 的 s 档注释里就写着「协议价」）
    assert.match(drawer, /scmDrawerWidth\('s'\)/);
    for (const title of ['定价对象', '价格', '有效期']) {
      assert.match(drawer, new RegExp(`scm-form-section__title">${title}<`), '抽屉缺少分区：' + title);
    }
  });

  test('价格历史：英文枚举必须落成中文，时间不上隐藏', () => {
    const page = code('../src/views/business/scm/pricing/price-history-list.vue');
    const display = code('../src/views/business/scm/pricing/pricing-display.ts');
    // 来源与变更类型都要有中文映射：直接渲染会把 AGREEMENT / CREATE 端给使用者
    assert.match(display, /AGREEMENT: '客户协议价'/);
    assert.match(display, /CUSTOMER_TYPE: '客户类型价'/);
    assert.match(display, /CREATE: '新增'/);
    assert.match(page, /historyLabel\(HISTORY_SOURCE_LABEL, record\.source\)/);
    assert.match(page, /historyLabel\(HISTORY_OPERATION_LABEL, record\.operationType\)/);
    assert.match(page, /HISTORY_OPERATION_TONE/);
    // 查询下拉与列表列共用同一份中文，不再各写一份字面量
    assert.match(page, /Object\.entries\(HISTORY_SOURCE_LABEL\)/);
    assert.match(page, /Object\.entries\(HISTORY_OPERATION_LABEL\)/);
    // 历史页是变更账本：生效 / 结束时间与变更时间就是它要回答的问题，不能隐藏
    assert.match(page, /title: '当前生效时间', dataIndex: 'currentEffectiveFrom'/);
    assert.match(page, /title: '当前结束时间', dataIndex: 'currentEffectiveTo'/);
    assert.match(page, /title: '变更时间', dataIndex: 'operatedAt'/);
    assert.match(page, /datetime\(record\.currentEffectiveFrom\)/);
    assert.match(page, /datetime\(record\.operatedAt\)/);
    // 操作人独立成列；规格编码不展示
    assert.doesNotMatch(page, /title: '商品规格编码'/);
    assert.match(page, /title: '操作人', dataIndex: 'operator'/);
  });

  test('价格预览：突出最终价格与来源，不展示命中的记录主键', () => {
    const page = code('../src/views/business/scm/pricing/price-preview.vue');
    assert.match(page, /'final-price'/);
    assert.match(page, /final-price--muted/);
    assert.match(page, /PRICE_SOURCE_TONE/);
    assert.match(page, /AGREEMENT: 'processing'/);
    assert.match(page, /MARKET: 'neutral'/);
    assert.match(page, /ScmStatusTag/);
    // sourceRecordId 是命中的那条价格记录的数据库主键：对使用者没有信息量，
    // 需要追溯时走价格历史页
    assert.doesNotMatch(page, /sourceRecordId/);
    assert.doesNotMatch(page, /title: '来源记录'/);
    // 规格编码不展示，只保留规格名称
    assert.doesNotMatch(page, /title: '商品规格编码'/);
    assert.doesNotMatch(page, /record\.skuCode/);
  });
}

// ---------------------------------------------------------------- 来源：pricing-batch-error-column.test.mjs
{
  const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
  const page = read('../src/views/business/scm/pricing/customer-type-price-batch.vue');

  test('批量调价的错误列只在整批失败后出现，平时不占一列空白', () => {
    const base = page.match(/const BASE_COLUMNS[\s\S]*?\n\];/)?.[0] ?? '';
    assert.ok(base, '基础列没有独立出来，错误列无法按需插入');
    assert.doesNotMatch(base, /title: '错误'/, '错误列仍常驻在基础列里');
    assert.match(page, /if \(!failures\.value\.length\) \{\s*return BASE_COLUMNS;/, '没有失败时仍渲染错误列');
    assert.match(page, /list\.splice\(5, 0, \{title: '错误'/, '有失败时错误列没有插回表格');
  });
}
