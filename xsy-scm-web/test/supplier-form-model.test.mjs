/*
 * 供应商表单模型单测
 *
 * 来源：**W1 派生** —— 运行方式与断言风格照抄 `test/product-form-model.test.mjs`。
 *
 * 重点覆盖两条**反直觉**的业务规则，它们是 W2 最容易写错的地方：
 * 1. legacy R12：同一供应商允许多条 `defaultFlag = true`，前端绝不能做单选限制；
 * 2. `supplier_sku` 是整表替换：已存在的行必须带 `id` + `version`，新增行不能带。
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  emptySkuDraft,
  emptySupplier,
  fromRows,
  removeSkuDraft,
  toReplaceItems,
  toSupplierPayload,
  validateSkuDrafts,
  validateSupplier,
} from '../src/views/business/scm/supplier/supplier-form-model.ts';

test('新建供应商不含状态字段，且新行默认启用且非默认来源', () => {
  const form = emptySupplier();
  // 状态不在表单里：新建强制 ENABLED（legacy S7），变更走独立的 updateStatus。
  assert.equal('status' in form, false);
  assert.equal(form.supplierCode, '');
  // 区划六列必须由新建初值显式置 null：漏一列（undefined）就会让上一条记录的区划串进新供应商。
  for (const key of ['provinceCode', 'provinceName', 'cityCode', 'cityName', 'districtCode', 'districtName']) {
    assert.equal(form[key], null, `emptySupplier 缺少区划列 ${key}`);
  }

  const draft = emptySkuDraft();
  assert.equal(draft.defaultFlag, false);
  assert.equal(draft.status, 'ENABLED');
  assert.equal(draft.referencePrice, null);
  assert.equal(draft.purchaserId, null);
});

test('服务端行转草稿时保留 id 与 version，否则保存会退化成「全新增」', () => {
  const drafts = fromRows([
    {
      id: 5,
      version: 3,
      skuId: 9,
      purchaseUnit: 'kg',
      referencePrice: '3.5000',
      purchaserId: 2,
      defaultFlag: true,
      status: 'ENABLED',
    },
  ]);
  assert.deepEqual(drafts[0], {
    id: 5,
    version: 3,
    skuId: 9,
    purchaseUnit: 'kg',
    referencePrice: '3.5000',
    purchaserId: 2,
    defaultFlag: true,
    status: 'ENABLED',
  });
});

test('校验供应商必填项与电话格式', () => {
  assert.match(validateSupplier(emptySupplier()), /供应商编码/);
  assert.match(validateSupplier({ ...emptySupplier(), supplierCode: 'S001' }), /供应商名称/);
  assert.equal(validateSupplier({ ...emptySupplier(), supplierCode: 'S001', name: '供应商甲' }), undefined);
  assert.match(validateSupplier({ ...emptySupplier(), supplierCode: 'S001', name: '甲', contactPhone: 'abc' }), /联系电话/);
});

test('校验关联行：规格必选、不得重复、单位必填、参考价最多四位小数', () => {
  assert.equal(validateSkuDrafts([]), undefined);

  assert.match(validateSkuDrafts([{ purchaseUnit: 'kg', defaultFlag: false }]), /请选择商品规格/);
  assert.match(
    validateSkuDrafts([
      { skuId: 1, purchaseUnit: 'kg', defaultFlag: false },
      { skuId: 1, purchaseUnit: '箱', defaultFlag: false },
    ]),
    /不能重复添加/
  );
  assert.match(validateSkuDrafts([{ skuId: 1, purchaseUnit: '   ', defaultFlag: false }]), /采购单位/);
  assert.match(
    validateSkuDrafts([{ skuId: 1, purchaseUnit: 'kg', referencePrice: '1.23456', defaultFlag: false }]),
    /四位小数/
  );
  assert.equal(validateSkuDrafts([{ skuId: 1, purchaseUnit: 'kg', referencePrice: '3.5000', defaultFlag: false }]), undefined);
});

test('R12：多条默认来源必须被接受，前端不做单选限制', () => {
  const items = toReplaceItems([
    { id: 1, version: 2, skuId: 10, purchaseUnit: 'kg', referencePrice: '3.5000', purchaserId: null, defaultFlag: true },
    { skuId: 11, purchaseUnit: '箱', referencePrice: '', purchaserId: 3, defaultFlag: true },
  ]);

  assert.equal(items.length, 2);
  assert.equal(items[0].defaultFlag, true);
  assert.equal(items[1].defaultFlag, true);
  // 校验函数也必须放行这个组合。
  assert.equal(
    validateSkuDrafts([
      { skuId: 10, purchaseUnit: 'kg', defaultFlag: true },
      { skuId: 11, purchaseUnit: '箱', defaultFlag: true },
    ]),
    undefined
  );
});

test('整表替换请求：已有行带 id+version，新增行不带，空白参考价转 null', () => {
  const items = toReplaceItems([
    { id: 1, version: 2, skuId: 10, purchaseUnit: ' kg ', referencePrice: null, purchaserId: null, defaultFlag: false },
    { skuId: 11, purchaseUnit: '箱', referencePrice: '   ', purchaserId: 3, defaultFlag: false },
  ]);

  assert.equal(items[0].id, 1);
  assert.equal(items[0].version, 2);
  assert.equal(items[0].purchaseUnit, 'kg');
  // 新增行绝不能带 id，否则后端会当成「更新一个不存在的行」。
  assert.equal('id' in items[1], false);
  assert.equal('version' in items[1], false);
  assert.equal(items[1].referencePrice, null);
  assert.equal(items[1].status, 'ENABLED');
  assert.equal(items[1].purchaserId, 3);
});

test('移除行是纯函数，不改动入参数组', () => {
  const drafts = [
    { purchaseUnit: 'a', defaultFlag: false },
    { purchaseUnit: 'b', defaultFlag: false },
  ];
  const left = removeSkuDraft(drafts, 0);
  assert.equal(left.length, 1);
  assert.equal(left[0].purchaseUnit, 'b');
  assert.equal(drafts.length, 2);
});

test('提交前归一化：编码大写去空白、空白串转 null', () => {
  const payload = toSupplierPayload({ ...emptySupplier(), supplierCode: ' s001 ', name: ' 供应商甲 ', address: '  ', remark: ' 备注 ' });
  assert.equal(payload.supplierCode, 'S001');
  assert.equal(payload.name, '供应商甲');
  assert.equal(payload.address, null);
  assert.equal(payload.remark, '备注');
});
