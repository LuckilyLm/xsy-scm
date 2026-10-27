import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmId, ScmPage} from '/@/types/business/scm/customer';
import type {
  PriceForm,
  PriceQuery,
  PriceRow,
  BatchRow,
  BatchResult,
  HistoryRow,
  ResolveResult,
  GroupPriceForm,
  GroupPriceQuery,
  GroupPriceRow,
} from '/@/types/business/scm/pricing';

function resource(path: string) {
  return {
    query: (form: PriceQuery, options?: RequestOptions) => postRequest<ScmPage<PriceRow>>(path + '/query', form, options),
    detail: (id: ScmId, options?: RequestOptions) => getRequest<PriceRow>(path + '/detail/' + id, {}, options),
    add: (form: PriceForm) => postRequest(path + '/add', form),
    update: (form: PriceForm) => postRequest(path + '/update', form),
    delete: (form: PriceForm) => postRequest(path + '/delete', form),
  };
}

export const pricingApi = {
  trial: (form: {customerId: ScmId; skuIds: ScmId[]; warehouseId: ScmId; at?: string | null}) =>
    postRequest<
      {
        skuId: ScmId;
        unitPrice: string | null;
        priceSource: string | null;
        unpricedReason: string | null;
        calculationBasis: Record<string, unknown> | null;
      }[]
    >('/scm/pricing/resolve/trial', form),
  agreement: resource('/scm/pricing/agreement-price'),
  groupPrice: {
    query: (form: GroupPriceQuery, options?: RequestOptions) => postRequest<ScmPage<GroupPriceRow>>('/scm/pricing/group-price/query', form, options),
    detail: (id: ScmId, options?: RequestOptions) => getRequest<GroupPriceRow>('/scm/pricing/group-price/detail/' + id, {}, options),
    add: (form: GroupPriceForm) => postRequest('/scm/pricing/group-price/add', form),
    update: (form: GroupPriceForm) => postRequest('/scm/pricing/group-price/update', form),
    delete: (form: GroupPriceForm) => postRequest('/scm/pricing/group-price/delete', form),
  },
  typePrice: resource('/scm/pricing/type-price'),
  batch: (form: {batchKey: string; rows: BatchRow[]}) => postRequest<BatchResult>('/scm/pricing/type-price/batch', form),
  history: (
    form: PriceQuery & {
      source?: string;
      operationType?: string;
      operatedFrom?: string | null;
      operatedTo?: string | null;
    },
    options?: RequestOptions
  ) => postRequest<ScmPage<HistoryRow>>('/scm/pricing/history/query', form, options),
  resolve: (
    form: {
      customerId: ScmId;
      skuIds: ScmId[];
      at?: string | null;
      warehouseId?: ScmId;
    },
    options?: RequestOptions
  ) => postRequest<ResolveResult>('/scm/pricing/resolve', form, options),
};
