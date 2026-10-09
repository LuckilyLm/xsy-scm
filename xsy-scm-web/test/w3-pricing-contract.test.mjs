import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import { formatAmount, formatAmountOrDash } from '../src/utils/scm-amount.ts';

/** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
}

test('amount contract keeps zero distinct from missing price', () => {
  assert.equal(formatAmount('0.0000'), '¥ 0.0000');
  assert.equal(formatAmount(null), '未定价');
  assert.equal(formatAmount(undefined), '未定价');
  assert.equal(formatAmount('1234567.5'), '¥ 1,234,567.5000');
  assert.equal(formatAmountOrDash(null), '—');
});

test('status contract has one unpriced reason and five unavailable reasons', async () => {
  const constants = await import('../src/constants/business/scm/pricing-const.ts');
  assert.deepEqual(Object.keys(constants.UNPRICED_REASON_ENUM), ['NO_PRICE_SOURCE']);
  assert.deepEqual(Object.keys(constants.UNAVAILABLE_REASON_ENUM).sort(), [
    'CATEGORY_DISABLED', 'NOT_VISIBLE', 'SKU_NOT_FOUND', 'SKU_OFF_SHELF', 'SPU_OFF_SHELF',
  ]);
});

test('pricing error mapping preserves overlap, conflict, idempotency and sellability codes', async () => {
  const { pricingError } = await import('../src/views/business/scm/pricing/pricing-errors.ts');
  assert.match(pricingError({ code: 40933 }), /重叠/);
  assert.match(pricingError({ code: 40935 }), /重叠/);
  assert.match(pricingError({ code: 40921 }), /修改/);
  assert.match(pricingError({ code: 40948 }), /批次号/);
  assert.match(pricingError({ code: 40949 }), /不可售/);
});

// ------------------------------------------------------------------ 价格中心展示

test('协议价抽屉：单价用 InputNumber + ¥，宽度走 foundation 分级', () => {
  const drawer = code('../src/views/business/scm/pricing/components/agreement-price-form-drawer.vue');
  assert.match(drawer, /<a-input-number[\s\S]{0,240}unitPrice/);
  assert.match(drawer, /addon-before="¥"/);
  assert.match(drawer, /:precision="4"/);
  assert.match(drawer, /:min="0"/);
  // 提交前必须收口回 4 位定点字符串：后端拒绝 JSON 数字，也拒绝空串
  assert.match(drawer, /form\.unitPrice = fixed4\(unitPrice\.value\) as string/);
  assert.match(drawer, /fixed4.*from '\.\.\/\.\.\/common\/scm-fixed'/);
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
  // 历史页是变更账本：生效时间与变更时间就是它要回答的问题，不能隐藏
  assert.match(page, /title: '当前有效期'/);
  assert.match(page, /title: '变更时间'/);
  assert.match(page, /datetime\(record\.currentEffectiveFrom\)/);
  assert.match(page, /datetime\(record\.operatedAt\)/);
  // 商品规格编码与操作人下沉为次要行，不再各占一列
  assert.match(page, /column\.dataIndex==='sku'[\s\S]{0,240}record\.skuCode/);
  assert.match(page, /column\.dataIndex==='operatedAt'[\s\S]{0,240}record\.operator/);
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
  // 规格编码下沉为规格名的次要行
  assert.match(page, /column\.dataIndex==='sku'[\s\S]{0,240}record\.skuCode/);
});
