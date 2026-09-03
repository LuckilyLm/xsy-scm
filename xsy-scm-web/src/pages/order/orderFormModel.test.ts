import { describe, expect, it } from 'vitest';
import type { SalesOrder } from '../../types/sales';
import {
  addOrderItem,
  createEmptyOrderForm,
  orderDetailToForm,
  removeOrderItem,
  setManualPrice,
  toOrderPayload,
  validateOrderForm,
} from './orderFormModel';

const detail: SalesOrder = {
  id: 12, version: 3, orderNo: 'SO202609030001', customerId: 8, customerName: '青禾餐饮',
  status: 'DRAFT', source: 'NORMAL', supplementReason: null, originalOrderId: null,
  totalAmount: '24.0000', createdAt: '2026-09-03T08:00:00Z', updatedAt: '2026-09-03T08:00:00Z',
  items: [{ id: 21, version: 2, skuId: 6, productId: 4, skuCode: 'SKU-TOMATO', productName: '西红柿', specName: '筐装', specValues: { size: '中果' }, saleUnit: 'kg', productType: 'NON_STANDARD', orderedQuantity: '2.0000', actualQuantity: null, unitPrice: '12.0000', lockedUnitPrice: null, priceSource: 'MARKET', priceSourceId: null, amount: null, overrideReason: null }],
};

describe('orderFormModel', () => {
  it('updates rows immutably and retains persisted identity', () => {
    const form = orderDetailToForm(detail);
    const updated = setManualPrice(form, form.items[0].key, '11.5000', '临时议价');
    expect(updated).not.toBe(form);
    expect(updated.items[0]).not.toBe(form.items[0]);
    expect(updated.items[0]).toMatchObject({ id: 21, version: 2, unitPrice: '11.5000', priceSource: 'OVERRIDE', overrideReason: '临时议价' });
    expect(toOrderPayload(updated).items[0]).toMatchObject({ id: 21, version: 2 });
  });

  it('adds and removes rows without mutating the source form', () => {
    const form = createEmptyOrderForm();
    const added = addOrderItem(form, { skuId: 6, skuCode: 'SKU-TOMATO', productName: '西红柿', saleUnit: 'kg', productType: 'NON_STANDARD', marketPrice: '12.0000', status: 'ON_SHELF' });
    expect(form.items).toHaveLength(0);
    expect(added.items).toHaveLength(1);
    expect(removeOrderItem(added, added.items[0].key).items).toHaveLength(0);
  });

  it('requires an override reason and supplement reason', () => {
    let form = orderDetailToForm(detail);
    form = setManualPrice(form, form.items[0].key, '11.5000', '');
    expect(validateOrderForm(form)).toBe('人工改价必须填写原因');
    expect(validateOrderForm({ ...form, items: [{ ...form.items[0], overrideReason: '议价' }], source: 'SUPPLEMENT', supplementReason: '' })).toBe('补单必须填写补单原因');
  });
});
