/*
 * 客户表单模型单测
 *
 * 来源：**W1 派生** —— 运行方式与断言风格照抄 `test/product-form-model.test.mjs`
 * （`node --experimental-strip-types --test`，直接 import `.ts` 源码）。
 *
 * 覆盖的是**后端会拒绝的非法组合**，也就是前端必须提前挡住的那些：
 * 账期三形态互斥、金额格式、可空字段的 `null` 语义。
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  applyCreditPeriodType,
  applyCreditPeriodUnit,
  emptyCustomer,
  toCustomerPayload,
  validateCustomer,
} from '../src/views/business/scm/customer/customer-form-model.ts';

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

  const cleared = applyCreditPeriodType({ ...dirty, creditPeriodType: 'BY_TIME' }, undefined);
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
  const ok = { ...emptyCustomer(), customerCode: 'C001', name: '客户甲', customerTypeId: 1 };
  assert.equal(validateCustomer(ok), undefined);

  assert.match(validateCustomer(emptyCustomer()), /客户编码/);
  assert.match(validateCustomer({ ...ok, customerCode: '   ' }), /客户编码/);
  assert.match(validateCustomer({ ...ok, name: '' }), /客户名称/);
  assert.match(validateCustomer({ ...ok, customerTypeId: undefined }), /客户类型/);
  assert.match(validateCustomer({ ...ok, contactPhone: '12345' }), /联系电话/);
  assert.match(validateCustomer({ ...ok, creditLimit: '1.23456' }), /四位小数/);
  assert.match(validateCustomer({ ...ok, creditLimit: '-1' }), /四位小数/);
  // 合法的 4 位定点数必须放行。
  assert.equal(validateCustomer({ ...ok, creditLimit: '1234.5000' }), undefined);
  assert.equal(validateCustomer({ ...ok, contactPhone: '13800138000' }), undefined);
});

test('按金额账期必须有阈值，按时间账期必须有值且结算日限 1–28', () => {
  const ok = { ...emptyCustomer(), customerCode: 'C001', name: '客户甲', customerTypeId: 1 };

  assert.match(validateCustomer({ ...ok, creditPeriodType: 'BY_AMOUNT' }), /金额阈值/);
  assert.match(validateCustomer({ ...ok, creditPeriodType: 'BY_AMOUNT', creditAmountThreshold: '1.23456' }), /四位小数/);
  assert.equal(validateCustomer({ ...ok, creditPeriodType: 'BY_AMOUNT', creditAmountThreshold: '5000.0000' }), undefined);

  assert.match(validateCustomer({ ...ok, creditPeriodType: 'BY_TIME' }), /账期值/);
  assert.match(validateCustomer({ ...ok, creditPeriodType: 'BY_TIME', creditPeriodValue: 0 }), /账期值/);
  // 有账期值但没单位：后端 ck_customer_credit_period 不接受，前端必须提前挡住。
  assert.match(validateCustomer({ ...ok, creditPeriodType: 'BY_TIME', creditPeriodValue: 30 }), /账期单位/);
  assert.equal(validateCustomer({ ...ok, creditPeriodType: 'BY_TIME', creditPeriodValue: 30, creditPeriodUnit: 'DAY' }), undefined);

  const monthly = { ...ok, creditPeriodType: 'BY_TIME', creditPeriodValue: 1, creditPeriodUnit: 'MONTH' };
  assert.equal(validateCustomer({ ...monthly, settleDay: 28 }), undefined);
  assert.match(validateCustomer({ ...monthly, settleDay: 29 }), /1 到 28/);
  assert.match(validateCustomer({ ...monthly, settleDay: 0 }), /1 到 28/);
});

test('提交前归一化：编码大写去空白、空白串转 null、零额度保持字符串', () => {
  const payload = toCustomerPayload({
    ...emptyCustomer(),
    customerCode: '  c001 ',
    name: '  客户甲  ',
    customerTypeId: 1,
    contactName: '   ',
    remark: ' 有备注 ',
  });

  assert.equal(payload.customerCode, 'C001');
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
