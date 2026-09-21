import type {PriceForm, BatchRow} from '../../../../types/business/scm/pricing';

export function emptyPrice(): PriceForm {
    return {unitPrice: '', effectiveFrom: '', effectiveTo: null};
}

export function validatePrice(f: PriceForm, dimension: 'customerId' | 'customerTypeId'): string[] {
    const errors: string[] = [];
    if (f[dimension] == null) errors.push(dimension === 'customerId' ? '请选择客户' : '请选择客户类型');
    if (f.skuId == null) errors.push('请选择 SKU');
    if (!/^\d{1,14}(\.\d{1,4})?$/.test(f.unitPrice)) errors.push('单价须为非负数，最多四位小数');
    if (!f.effectiveFrom || !Number.isFinite(Date.parse(f.effectiveFrom))) errors.push('请选择生效时间');
    if (f.effectiveTo && (!Number.isFinite(Date.parse(f.effectiveTo)) || Date.parse(f.effectiveTo) <= Date.parse(f.effectiveFrom))) errors.push('结束时间必须晚于开始时间');
    return errors;
}

export function validateBatch(rows: BatchRow[]): { rowNumber: number; message: string }[] {
    return rows.flatMap(r => validatePrice(r, 'customerTypeId').map(message => ({rowNumber: r.rowNumber, message})));
}
