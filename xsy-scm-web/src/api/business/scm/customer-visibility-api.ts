import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmId, ScmResponse, ScmPage} from '/@/types/business/scm/customer';
import type {VisibilityRow} from '/@/types/business/scm/pricing';

export const customerVisibilityApi = {
    query: (form: {
        pageNum: number;
        pageSize: number;
        customerId?: ScmId;
        skuId?: ScmId;
        visibilityPolicy?: string
    }, options?: RequestOptions) =>
        postRequest('/scm/customer/visibility/reverse/query', form, options) as unknown as Promise<ScmResponse<ScmPage<VisibilityRow>>>
};
