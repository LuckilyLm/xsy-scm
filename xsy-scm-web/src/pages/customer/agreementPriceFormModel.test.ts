import {describe, expect, it} from 'vitest';
import {normalizeAgreementPricePayload, validateAgreementPeriod} from './agreementPriceFormModel';

describe('agreement price form model', () => {
    it('keeps decimal text and version in the payload', () => {
        expect(normalizeAgreementPricePayload({
            version: 2,
            customerId: 8,
            skuId: 10,
            unitPrice: '6.5000',
            effectiveFrom: '2026-09-03T00:00:00+08:00',
            effectiveTo: null,
        })).toEqual({
            version: 2,
            customerId: 8,
            skuId: 10,
            unitPrice: '6.5000',
            effectiveFrom: '2026-09-03T00:00:00+08:00',
            effectiveTo: null,
        });
    });

    it('rejects a closed period whose end is not after its start', () => {
        expect(validateAgreementPeriod('2026-09-04T00:00:00+08:00', '2026-09-03T00:00:00+08:00'))
            .toBe('结束时间必须晚于生效时间');
    });
});
