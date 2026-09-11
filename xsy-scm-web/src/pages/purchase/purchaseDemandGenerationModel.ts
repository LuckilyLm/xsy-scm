import type {Dayjs} from 'dayjs';
import type {PurchaseDemandGenerationPayload} from '../../types/purchase';

export interface PurchaseDemandGenerationValues {
    warehouseId: number;
    timeRange: [Dayjs, Dayjs];
    calculateInventory?: boolean;
}

export function toGenerationPayload(values: PurchaseDemandGenerationValues): PurchaseDemandGenerationPayload {
    const [startAt, endAt] = values.timeRange;
    if (!startAt.isBefore(endAt)) {
        throw new Error('采购需求统计开始时间必须早于结束时间');
    }
    return {
        warehouseId: values.warehouseId,
        startAt: startAt.toISOString(),
        endAt: endAt.toISOString(),
        calculateInventory: values.calculateInventory ?? true,
    };
}
