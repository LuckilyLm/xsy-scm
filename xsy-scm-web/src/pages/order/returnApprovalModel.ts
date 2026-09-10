import type {ApproveReturnPayload, OrderReturn} from '../../types/sales';

export interface ReturnApprovalDraftItem {
    returnItemId: number;
    version: number;
    requestedQuantity: string;
    approvedQuantity: string;
}

export type ReturnApprovalDraft = ReturnApprovalDraftItem[];

const decimalPattern = /^\d+(\.\d{1,4})?$/;

export function createApprovalDraft(orderReturn: OrderReturn): ReturnApprovalDraft {
    return orderReturn.items.map((item) => ({
        returnItemId: item.id,
        version: item.version,
        requestedQuantity: item.requestedQuantity,
        approvedQuantity: item.requestedQuantity,
    }));
}

export function updateApprovalQuantity(draft: ReturnApprovalDraft, returnItemId: number, approvedQuantity: string): ReturnApprovalDraft {
    return draft.map((item) => item.returnItemId === returnItemId ? {...item, approvedQuantity} : item);
}

export function validateApprovalDraft(draft: ReturnApprovalDraft): string | null {
    for (const item of draft) {
        if (!decimalPattern.test(item.approvedQuantity)) return '批准数量格式不正确，最多四位小数';
        if (compareDecimal(item.approvedQuantity, item.requestedQuantity) > 0) return '批准数量不能超过申请数量';
    }
    if (!draft.some((item) => /[1-9]/.test(item.approvedQuantity))) return '至少一行批准数量必须大于零';
    return null;
}

function compareDecimal(left: string, right: string): number {
    const normalize = (value: string) => {
        const [integer, fraction = ''] = value.split('.');
        return {integer: integer.replace(/^0+(?=\d)/, ''), fraction: fraction.padEnd(4, '0')};
    };
    const a = normalize(left);
    const b = normalize(right);
    if (a.integer.length !== b.integer.length) return a.integer.length - b.integer.length;
    const integerComparison = a.integer.localeCompare(b.integer);
    return integerComparison !== 0 ? integerComparison : a.fraction.localeCompare(b.fraction);
}

export function toApproveReturnPayload(version: number, draft: ReturnApprovalDraft): ApproveReturnPayload {
    return {
        version,
        items: draft.map((item) => ({
            returnItemId: item.returnItemId,
            version: item.version,
            approvedQuantity: item.approvedQuantity.trim(),
        })),
    };
}
