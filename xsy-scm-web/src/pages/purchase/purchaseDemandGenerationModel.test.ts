import dayjs from 'dayjs';
import {describe, expect, it} from 'vitest';
import {toGenerationPayload} from './purchaseDemandGenerationModel';

describe('purchase demand generation model', () => {
    it('maps half-open range and defaults inventory calculation on', () => {
        const result = toGenerationPayload({warehouseId: 3, timeRange: [dayjs('2026-09-01T00:00:00Z'), dayjs('2026-09-02T00:00:00Z')]});
        expect(result).toMatchObject({warehouseId: 3, calculateInventory: true});
        expect(result.startAt).toContain('2026-09-01');
    });
    it('rejects an empty or reversed range', () => {
        expect(() => toGenerationPayload({warehouseId: 3, timeRange: [dayjs('2026-09-02'), dayjs('2026-09-02')]})).toThrow();
    });
});
