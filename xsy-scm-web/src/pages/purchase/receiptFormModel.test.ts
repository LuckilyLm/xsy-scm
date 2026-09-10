import {describe, expect, it} from 'vitest';
import {remaining, toReceiptConfirmPayload} from './receiptFormModel';
import type {PurchaseReceiptItem} from '../../types/purchase';

const item = (type: 'STANDARD' | 'NON_STANDARD'): PurchaseReceiptItem => ({
    id: 1,
    purchaseOrderItemId: 2,
    skuId: 3,
    skuCodeSnapshot: 'S',
    skuNameSnapshot: 'N',
    productNameSnapshot: 'P',
    purchaseUnitSnapshot: 'kg',
    productTypeSnapshot: type,
    receivedQuantity: '2.0000',
    version: 4
});
describe('receiptFormModel', () => {
    it('computes remaining quantity', () => expect(remaining('5.0000', '2.0000')).toBe('3.0000'));
    it('computes large quantities without JavaScript number precision loss', () => expect(remaining('99999999999999.9999', '0.0001')).toBe('99999999999999.9998'));
    it('maps standard quantity', () => expect(toReceiptConfirmPayload(1, [item('STANDARD')], {1: {receivedQuantity: '1.5000'}}).items[0]).toMatchObject({
        receivedQuantity: '1.5000',
        actualWeight: null
    }));
    it('uses manual actual weight for non-standard item', () => expect(toReceiptConfirmPayload(1, [item('NON_STANDARD')], {
        1: {
            receivedQuantity: '2.0000',
            actualWeight: '1.8600',
            correctionReason: '去包装'
        }
    }).items[0]).toMatchObject({actualWeight: '1.8600', weightSource: 'MANUAL'}));
});
