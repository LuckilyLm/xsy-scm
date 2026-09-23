/**
 * Wave 3 订单录单效率纯模型单测：草稿压缩 / 还原 / 历史复用。
 *
 * 这三件事都涉及「什么能存、什么绝不能存」的安全边界，违背后不报错但会造成
 * 草稿互串、把历史价 / version 当新单事实等隐患，因此对纯函数逐条钉死：
 * 1. 草稿只存用户录入，绝不落 PriceResolver 解析价、lockedUnitPrice、version、status、快照；
 * 2. 恢复只回填用户字段，解析价留空由调用方重新请求；
 * 3. 历史复用不复制 orderId / orderNo / version / status / 历史价 / 人工改价，来源回落后台录单。
 */
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {
  newOrder,
  draftKey,
  serializeDraft,
  applyDraft,
  fromHistory,
} from '../src/views/business/scm/order/order-form-model.ts';

function itemOf(over) {
  return {orderedQuantity: '2.0000', manualPriceOverride: false, ...over};
}

test('草稿键按登录用户隔离，不同操作员互不串', () => {
  assert.equal(draftKey(44), 'xsy-scm:order-draft:44');
  assert.notEqual(draftKey('44'), draftKey('45'));
});

test('serializeDraft 丢弃一切服务端解析 / 快照 / version / 状态字段', () => {
  const form = {
    ...newOrder(),
    orderId: 99,
    orderNo: 'SO-1',
    version: 7,
    status: 'DRAFT',
    customerId: 12,
    orderSource: 'ADMIN',
    expectDeliveryTime: '2026-09-22T00:00:00Z',
    remark: 'r',
    items: [
      itemOf({
        skuId: 5,
        itemId: 3,
        version: 2,
        draftUnitPrice: '8.0000',
        draftPriceSource: 'MARKET',
        lockedUnitPrice: '8.0000',
        productNameSnapshot: 'P',
        saleUnitSnapshot: 'kg',
      }),
    ],
  };
  const draft = serializeDraft(form);
  assert.equal(draft.orderId, undefined);
  assert.equal(draft.orderNo, undefined);
  assert.equal(draft.version, undefined);
  assert.equal(draft.status, undefined);
  const line = draft.items[0];
  // 解析价 / 锁定价 / 快照 / 行 version 一律不进草稿
  assert.equal(line.draftUnitPrice, undefined);
  assert.equal(line.lockedUnitPrice, undefined);
  assert.equal(line.productNameSnapshot, undefined);
  assert.equal(line.version, undefined);
  assert.equal(line.itemId, undefined);
  // 保留用户录入
  assert.equal(line.skuId, 5);
  assert.equal(line.orderedQuantity, '2.0000');
});

test('serializeDraft 只在补单来源下保留补单上下文，并过滤空行', () => {
  const base = {
    ...newOrder(),
    customerId: 12,
    items: [
      {orderedQuantity: '', manualPriceOverride: false},
      {orderedQuantity: '1.0000', manualPriceOverride: false, skuId: 8},
      {orderedQuantity: '', manualPriceOverride: false, skuId: 9},
    ],
  };
  const admin = serializeDraft({...base, orderSource: 'ADMIN', supplementReason: 'x', originalOrderId: 3});
  assert.equal(admin.supplementReason, null);
  assert.equal(admin.originalOrderId, null);
  // 只丢弃「无 SKU 且无数量」的占位行；有 SKU 或有数量都算用户在录的半行
  assert.equal(admin.items.length, 2);

  const sup = serializeDraft({...base, orderSource: 'SUPPLEMENT', supplementReason: 'x', originalOrderId: 3});
  assert.equal(sup.supplementReason, 'x');
  assert.equal(sup.originalOrderId, 3);
});

test('serializeDraft 仅在人工改价开启时保留单价与原因', () => {
  const form = {...newOrder(), items: [itemOf({skuId: 5, manualPriceOverride: true, unitPrice: '6.6000', overrideReason: '促销'})]};
  assert.deepEqual(serializeDraft(form).items[0], {
    skuId: 5,
    orderedQuantity: '2.0000',
    manualPriceOverride: true,
    unitPrice: '6.6000',
    overrideReason: '促销',
  });
  const off = {...newOrder(), items: [itemOf({skuId: 5, manualPriceOverride: false, unitPrice: '6.6000'})]};
  assert.equal(serializeDraft(off).items[0].unitPrice, null);
});

test('applyDraft 还原为新建草稿：保留用户字段，来源缺省回落 ADMIN', () => {
  const draft = {
    customerId: 12,
    orderSource: 'ADMIN',
    expectDeliveryTime: '2026-09-22T00:00:00Z',
    remark: 'r',
    supplementReason: null,
    originalOrderId: null,
    items: [{skuId: 5, orderedQuantity: '3.0000', manualPriceOverride: false}],
  };
  const form = applyDraft(draft);
  assert.equal(form.orderId, undefined);
  assert.equal(form.customerId, 12);
  assert.equal(form.items.length, 1);
  assert.equal(form.items[0].orderedQuantity, '3.0000');
  // 解析价不在草稿里，恢复后由 preview 重填
  assert.equal(form.items[0].draftUnitPrice, undefined);
});

test('fromHistory 复制允许字段但绝不带历史价 / 状态 / version / 人工改价', () => {
  const detail = {
    ...newOrder(),
    orderId: 55,
    orderNo: 'SO-55',
    version: 9,
    status: 'CONFIRMED',
    customerId: 12,
    orderSource: 'MALL',
    expectDeliveryTime: '2026-09-22T00:00:00Z',
    remark: 'r',
    address: {receiverName: '张三', receiverPhone: '139', address: '路 1 号'},
    items: [
      itemOf({
        skuId: 5,
        itemId: 7,
        version: 4,
        orderedQuantity: '3.0000',
        manualPriceOverride: true,
        unitPrice: '1.1000',
        draftUnitPrice: '9.9000',
        lockedUnitPrice: '8.8000',
        lockedPriceSource: 'OVERRIDE',
      }),
    ],
  };
  const reuse = fromHistory(detail);
  assert.equal(reuse.orderId, undefined);
  assert.equal(reuse.orderNo, undefined);
  assert.equal(reuse.version, undefined);
  assert.equal(reuse.status, undefined);
  // 作为后台录单的新草稿重新提交
  assert.equal(reuse.orderSource, 'ADMIN');
  assert.equal(reuse.customerId, 12);
  assert.equal(reuse.expectDeliveryTime, '2026-09-22T00:00:00Z');
  assert.equal(reuse.remark, 'r');
  assert.deepEqual(reuse.address, {receiverName: '张三', receiverPhone: '139', address: '路 1 号'});
  const line = reuse.items[0];
  assert.equal(line.skuId, 5);
  assert.equal(line.orderedQuantity, '3.0000');
  // 历史价 / 人工改价 / 行身份绝不作为新单事实
  assert.equal(line.manualPriceOverride, false);
  assert.equal(line.unitPrice, null);
  assert.equal(line.itemId, undefined);
  assert.equal(line.version, undefined);
  assert.equal(line.draftUnitPrice, undefined);
  assert.equal(line.lockedUnitPrice, undefined);
});

test('fromHistory 在无地址的历史单上回退到空白地址而非 undefined', () => {
  const reuse = fromHistory({...newOrder(), address: null, customerId: 12, items: []});
  assert.deepEqual(reuse.address, {receiverName: '', receiverPhone: '', address: ''});
});
