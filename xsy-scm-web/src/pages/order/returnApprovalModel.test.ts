import {describe, expect, it} from 'vitest';
import type {OrderReturn} from '../../types/sales';
import {
    createApprovalDraft,
    toApproveReturnPayload,
    updateApprovalQuantity,
    validateApprovalDraft
} from './returnApprovalModel';

const orderReturn: OrderReturn = {
    id: 9,
    version: 3,
    returnNo: 'RT202609040001',
    orderId: 7,
    customerId: 2,
    status: 'PENDING',
    reason: '部分破损',
    decisionReason: null,
    approvedAmount: '0.0000',
    items: [
        {
            id: 11,
            orderItemId: 21,
            requestedQuantity: '3.0000',
            approvedQuantity: null,
            lockedUnitPrice: '5.0000',
            approvedAmount: '0.0000',
            version: 1
        },
        {
            id: 12,
            orderItemId: 22,
            requestedQuantity: '2.0000',
            approvedQuantity: null,
            lockedUnitPrice: '8.0000',
            approvedAmount: '0.0000',
            version: 0
        },
    ],
};

describe('returnApprovalModel', () => {
    it('supports approving a smaller quantity on each return line', () => {
        const draft = updateApprovalQuantity(createApprovalDraft(orderReturn), 11, '1.5000');

        expect(validateApprovalDraft(draft)).toBeNull();
        expect(toApproveReturnPayload(orderReturn.version, draft)).toEqual({
            version: 3,
            items: [
                {returnItemId: 11, version: 1, approvedQuantity: '1.5000'},
                {returnItemId: 12, version: 0, approvedQuantity: '2.0000'},
            ],
        });
    });

    it('rejects totals with no positive approved quantity', () => {
        let draft = createApprovalDraft(orderReturn);
        draft = updateApprovalQuantity(draft, 11, '0');
        draft = updateApprovalQuantity(draft, 12, '0');

        expect(validateApprovalDraft(draft)).toBe('至少一行批准数量必须大于零');
    });

    it('compares large decimal strings without JavaScript number precision loss', () => {
        const draft = [{
            returnItemId: 11,
            version: 1,
            requestedQuantity: '99999999999999.9998',
            approvedQuantity: '99999999999999.9999',
        }];

        expect(validateApprovalDraft(draft)).toBe('批准数量不能超过申请数量');
    });
});
