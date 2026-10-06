/**
 * 营销中心（活动 / 优惠券）前端契约单测。
 *
 * 只钉「违背之后页面照样能跑、但业务事实已经错了」的那一类：
 *
 * 1. **折扣率的展示换算无损**：后端存 (0,1] 的比率（0.95），表单展示百分比（95）。
 *    百分比取 2 位小数即可覆盖 4 位比率的全部取值，因此往返 `rate → percent → rate`
 *    必须逐字回到原值 —— 差一个 0.0001 就是「95 折」变成「95.01 折」。
 * 2. **规则是受控键值**：切换活动类型必须清空规则，否则残留的键会被服务端当成未知键拒收（41324）。
 * 3. **空值不进规则载荷**：多带一个当前类型用不上的键同样会被 41324 拒收。
 * 4. **编码下沉**：活动编码 / 券编码作为名称的 secondary text，不再各占一列。
 * 5. **门槛 0 是「无门槛」这个业务事实**，不是缺值。
 *
 * 扫描前剥掉注释：这些文件里大量出现反例说明，不剥注释会把纪律文档本身判成违规。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {percentToRate, rateToPercent} from '../src/views/business/scm/promotion/promotion-types.ts';

/** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
}

const activity = code('../src/views/business/scm/promotion/promotion-activity-list.vue');
const coupon = code('../src/views/business/scm/promotion/promotion-coupon-list.vue');

// ------------------------------------------------------------------
// 折扣率换算
// ------------------------------------------------------------------

test('折扣率的展示换算在 4 位比率的全值域上无损往返', () => {
  // 0.95 比率 = 95%，往返必须逐字回到 "0.9500"
  assert.equal(rateToPercent('0.9500'), 95);
  assert.equal(percentToRate(95), '0.9500');
  // 4 位比率的两个极值：0.0001 = 0.01%，0.9999 = 99.99%
  assert.equal(rateToPercent('0.0001'), 0.01);
  assert.equal(percentToRate(0.01), '0.0001');
  assert.equal(rateToPercent('0.9999'), 99.99);
  assert.equal(percentToRate(99.99), '0.9999');
  // 浮点陷阱：0.95 * 100 在 IEEE754 下是 95.00000000000001，必须收敛成 2 位小数
  assert.equal(rateToPercent('0.9500'), 95);
  assert.equal(rateToPercent('0.0700'), 7);
  // 全值域往返：4 位比率的每一个可能取值都要能原样回来
  for (let raw = 1; raw <= 9999; raw += 7) {
    const rate = (raw / 10000).toFixed(4);
    const back = percentToRate(rateToPercent(rate));
    assert.equal(back, rate, `比率 ${rate} 往返后变成 ${back}`);
  }
  // 「没填」与「填了 0」是两件事
  assert.equal(rateToPercent(null), null);
  assert.equal(rateToPercent(''), null);
  assert.equal(percentToRate(null), undefined);
});

// ------------------------------------------------------------------
// 活动
// ------------------------------------------------------------------

test('活动列表把编码折进名称、把生效与失效时间合成一格', () => {
  assert.doesNotMatch(activity, /title: '活动编码'/);
  assert.match(activity, /title: '活动名称', dataIndex: 'activityName'/);
  assert.match(activity, /column\.dataIndex === 'activityName'[\s\S]{0,240}record\.activityCode/);
  assert.doesNotMatch(activity, /title: '生效时间'/);
  assert.doesNotMatch(activity, /title: '失效时间'/);
  assert.match(activity, /title: '有效期', dataIndex: 'validity'/);
  assert.match(activity, /column\.dataIndex === 'validity'[\s\S]{0,240}record\.validTo/);
  // 状态走统一档位映射
  assert.match(activity, /DRAFT: 'warning'/);
  assert.match(activity, /ACTIVE: 'success'/);
  assert.match(activity, /STOPPED: 'neutral'/);
  assert.match(activity, /ScmStatusTag/);
});

test('活动规则的数值字段全部是 InputNumber，且提交时收口回定点字符串', () => {
  // 折扣率带 %、特价与满减带 ¥：语义化控件是硬要求
  assert.match(activity, /addon-after="%"/);
  assert.match(activity, /addon-before="¥"/);
  assert.match(activity, /v-model:value="rule\.discountPercent"/);
  assert.match(activity, /v-model:value="rule\.specialPrice"/);
  assert.match(activity, /v-model:value="rule\.thresholdAmount"/);
  assert.match(activity, /v-model:value="rule\.reduceAmount"/);
  assert.match(activity, /v-model:value="rule\.giftQuantity"/);
  // 赠品与特价的 SKU 用选择器，不是裸输入框
  assert.match(activity, /<SkuSelect[\s\S]{0,200}onSpecialSkuChange/);
  assert.match(activity, /<SkuSelect[\s\S]{0,200}onGiftSkuChange/);
  // 提交收口：比率走 percentToRate，金额 / 数量走 fixed4
  assert.match(activity, /put\('discountRate', percentToRate\(source\.discountPercent\)\)/);
  assert.match(activity, /put\('specialPrice', fixed4\(source\.specialPrice\)\)/);
  assert.match(activity, /put\('giftQuantity', fixed4\(source\.giftQuantity\)\)/);
  assert.doesNotMatch(activity, /toFixed\(/, '定点转换应统一走 fixed4 / percentToRate');
  // 分区：基础 / 规则 / 有效期 / 叠加互斥 / 备注
  for (const title of ['活动基础', '活动规则', '有效期', '叠加与互斥', '备注']) {
    assert.match(activity, new RegExp(`scm-form-section__title">${title}<`), '活动表单缺少分区：' + title);
  }
});

test('切换活动类型必须清空规则，空值不进载荷', () => {
  assert.match(activity, /function onTypeChange\(\)[\s\S]{0,200}Object\.assign\(rule, emptyRule\(\)\)/);
  assert.match(activity, /form\.rule = \{\}/);
  // 空值不进载荷：未知键会被服务端拒收（41324）
  assert.match(activity, /if \(value !== undefined\) out\[key\] = value/);
});

// ------------------------------------------------------------------
// 优惠券
// ------------------------------------------------------------------

test('优惠券列表把券编码折进名称，并把「无门槛」与「门槛 0」讲清楚', () => {
  assert.doesNotMatch(coupon, /title: '券编码'/);
  assert.match(coupon, /title: '券名称', dataIndex: 'couponName'/);
  assert.match(coupon, /column\.dataIndex === 'couponName'[\s\S]{0,240}record\.couponCode/);
  // 券类型独立成列（原来只混在「优惠」文字里）
  assert.match(coupon, /title: '券类型', dataIndex: 'discountType'/);
  // 门槛 0 = 无门槛，是业务事实而不是缺值
  assert.match(coupon, /function isNoThreshold/);
  assert.match(coupon, /无门槛/);
  // 生效与失效时间合成一格
  assert.match(coupon, /title: '有效期', dataIndex: 'validity'/);
  // 状态走统一档位映射
  assert.match(coupon, /ScmStatusTag/);
  assert.match(coupon, /DRAFT: 'warning'/);
});

test('优惠券的优惠值与门槛金额都是 InputNumber，切换券类型要清空量纲', () => {
  assert.match(coupon, /v-model:value="discountValue"/);
  assert.match(coupon, /v-model:value="minOrderAmount"/);
  assert.match(coupon, /addon-after="%"/);
  assert.match(coupon, /addon-before="¥"/);
  // 比率与金额是两种量纲：切换类型后残留值会被按新类型解释
  assert.match(coupon, /function onDiscountTypeChange\(\)[\s\S]{0,200}discountValue\.value = null/);
  // 提交收口
  assert.match(coupon, /percentToRate\(discountValue\.value\) \?\? ''/);
  assert.match(coupon, /fixed4\(minOrderAmount\.value\) \?\? '0'/);
  assert.doesNotMatch(coupon, /toFixed\(/, '定点转换应统一走 fixed4 / percentToRate');
  // 发券张数本来就是 InputNumber，别在重构里退回裸输入框
  assert.match(coupon, /<a-input-number[\s\S]{0,120}issueForm\.quantity/);
});

test('营销两个页面都不再用未定义的 --ant-color-* 变量', () => {
  for (const [name, source] of [['活动页', activity], ['优惠券页', coupon]]) {
    assert.doesNotMatch(source, /var\(--ant-color-/, `${name} 使用了从未定义的 --ant-color-*`);
  }
});
