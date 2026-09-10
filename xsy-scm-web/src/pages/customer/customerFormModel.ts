import type {
    CustomerDetail,
    CustomerPayload,
    CustomerSkuVisibility,
    CustomerStatus,
    VisibilityPolicy,
} from '../../types/sales';

export interface CustomerForm {
    version: number | null;
    customerCode: string;
    name: string;
    customerTypeId: number | null;
    status: CustomerStatus;
    visibilityPolicy: VisibilityPolicy;
    visibilitySkuIds: number[];
    retainedVisibilities: CustomerSkuVisibility[];
}

export function createEmptyCustomerForm(): CustomerForm {
    return {
        version: null,
        customerCode: '',
        name: '',
        customerTypeId: null,
        status: 'ENABLED',
        visibilityPolicy: 'ALL_ENABLED',
        visibilitySkuIds: [],
        retainedVisibilities: [],
    };
}

export function customerDetailToForm(detail: CustomerDetail): CustomerForm {
    return {
        version: detail.version,
        customerCode: detail.customerCode,
        name: detail.name,
        customerTypeId: detail.customerTypeId,
        status: detail.status,
        visibilityPolicy: detail.visibilityPolicy,
        visibilitySkuIds: detail.visibilities.map((item) => item.skuId),
        retainedVisibilities: detail.visibilities,
    };
}

export function normalizeCustomerPayload(form: CustomerForm): CustomerPayload {
    const retainedBySku = new Map(form.retainedVisibilities.map((item) => [item.skuId, item]));
    return {
        version: form.version,
        customerCode: form.customerCode.trim(),
        name: form.name.trim(),
        customerTypeId: form.customerTypeId!,
        status: form.status,
        visibilityPolicy: form.visibilityPolicy,
        visibilities: form.visibilityPolicy === 'ALLOWLIST'
            ? form.visibilitySkuIds.map((skuId) => {
                const retained = retainedBySku.get(skuId);
                return retained ? {id: retained.id, version: retained.version, skuId} : {skuId};
            })
            : [],
    };
}
