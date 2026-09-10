import type {AgreementPricePayload} from '../../types/sales';

export interface AgreementPriceForm {
    version: number | null;
    customerId: number | null;
    skuId: number | null;
    unitPrice: string;
    effectiveFrom: string;
    effectiveTo: string | null;
}

export function createEmptyAgreementPriceForm(): AgreementPriceForm {
    return {version: null, customerId: null, skuId: null, unitPrice: '', effectiveFrom: '', effectiveTo: null};
}

export function validateAgreementPeriod(from: string, to: string | null) {
    if (!from) return '请选择生效时间';
    if (to && new Date(to).getTime() <= new Date(from).getTime()) return '结束时间必须晚于生效时间';
    return null;
}

export function normalizeAgreementPricePayload(form: AgreementPriceForm): AgreementPricePayload {
    return {
        version: form.version,
        customerId: form.customerId!,
        skuId: form.skuId!,
        unitPrice: form.unitPrice.trim(),
        effectiveFrom: form.effectiveFrom,
        effectiveTo: form.effectiveTo || null,
    };
}
