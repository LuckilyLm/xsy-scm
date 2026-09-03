import { describe, expect, it } from 'vitest';
import type { CustomerDetail } from '../../types/sales';
import { customerDetailToForm, normalizeCustomerPayload } from './customerFormModel';

const detail: CustomerDetail = {
  id: 7,
  version: 3,
  customerCode: 'C-007',
  name: '春风食堂',
  customerTypeId: 2,
  customerTypeName: '餐饮客户',
  status: 'ENABLED',
  visibilityPolicy: 'ALLOWLIST',
  visibilities: [{ id: 19, version: 4, skuId: 101 }],
  updatedAt: '2026-09-03T08:00:00+08:00',
};

describe('customer form model', () => {
  it('preserves retained visibility identities and adds new rows without identity', () => {
    const form = customerDetailToForm(detail);
    form.visibilitySkuIds = [101, 102];

    expect(normalizeCustomerPayload(form)).toEqual({
      version: 3,
      customerCode: 'C-007',
      name: '春风食堂',
      customerTypeId: 2,
      status: 'ENABLED',
      visibilityPolicy: 'ALLOWLIST',
      visibilities: [
        { id: 19, version: 4, skuId: 101 },
        { skuId: 102 },
      ],
    });
  });

  it('sends no visibility rows for all-enabled policy', () => {
    const form = customerDetailToForm(detail);
    form.visibilityPolicy = 'ALL_ENABLED';
    expect(normalizeCustomerPayload(form).visibilities).toEqual([]);
  });
});
