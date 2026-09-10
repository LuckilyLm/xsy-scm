import type {PurchaseReceiptItem, ReceiptConfirmPayload} from '../../types/purchase';
import {isPositiveDecimal, subtractDecimal} from '../../utils/decimal';

export interface ReceiptInput {
    receivedQuantity: string;
    actualWeight?: string;
    correctionReason?: string;
}

export function remaining(planned: string, received: string) {
    return subtractDecimal(planned, received);
}

export function toReceiptConfirmPayload(version: number, items: PurchaseReceiptItem[], inputs: Record<number, ReceiptInput>): ReceiptConfirmPayload {
    const result: ReceiptConfirmPayload['items'] = [];
    for (const item of items) {
        const input = inputs[item.id];
        if (!input || !isPositiveDecimal(input.receivedQuantity)) continue;
        if (item.productTypeSnapshot === 'NON_STANDARD') {
            if (!input.actualWeight || !isPositiveDecimal(input.actualWeight)) continue;
            result.push({
                receiptItemId: item.id,
                version: item.version,
                receivedQuantity: input.receivedQuantity,
                actualWeight: input.actualWeight,
                weightSource: 'MANUAL',
                correctionReason: input.correctionReason || null
            });
        } else result.push({
            receiptItemId: item.id,
            version: item.version,
            receivedQuantity: input.receivedQuantity,
            actualWeight: null,
            weightSource: null,
            correctionReason: null
        });
    }
    return {version, items: result};
}
