import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmId, ScmResponse, ScmPage} from '/@/types/business/scm/customer';
import type {
    PriceForm,
    PriceQuery,
    PriceRow,
    BatchRow,
    BatchResult,
    HistoryRow,
    ResolveResult
} from '/@/types/business/scm/pricing';

function resource(path: string) {
    return {
        query: (form: PriceQuery, options?: RequestOptions) =>
            postRequest(path + '/query', form, options) as unknown as Promise<ScmResponse<ScmPage<PriceRow>>>,
        detail: (id: ScmId, options?: RequestOptions) =>
            getRequest(path + '/detail/' + id, {}, options) as unknown as Promise<ScmResponse<PriceRow>>,
        add: (form: PriceForm) => postRequest(path + '/add', form),
        update: (form: PriceForm) => postRequest(path + '/update', form),
        delete: (form: PriceForm) => postRequest(path + '/delete', form),
    };
}

export const pricingApi = {
    agreement: resource('/scm/pricing/agreement-price'), typePrice: resource('/scm/pricing/type-price'),
    batch: (form: {
        batchKey: string;
        rows: BatchRow[]
    }) => postRequest('/scm/pricing/type-price/batch', form) as unknown as Promise<ScmResponse<BatchResult>>,
    history: (form: PriceQuery & {
        source?: string;
        operationType?: string;
        operatedFrom?: string | null;
        operatedTo?: string | null
    }, options?: RequestOptions) =>
        postRequest('/scm/pricing/history/query', form, options) as unknown as Promise<ScmResponse<ScmPage<HistoryRow>>>,
    resolve: (form: {
        customerId: ScmId;
        skuIds: ScmId[];
        at?: string | null
    }, options?: RequestOptions) =>
        postRequest('/scm/pricing/resolve', form, options) as unknown as Promise<ScmResponse<ResolveResult>>,
};
