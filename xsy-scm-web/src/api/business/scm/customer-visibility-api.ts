import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmId, ScmResponse, ScmPage} from '/@/types/business/scm/customer';
import type {CustomerVisibilitySummary, VisibilityRow} from '/@/types/business/scm/pricing';

export const customerVisibilityApi = {
  /** 按客户汇总可见商品与规格数，列表页的客户卡片使用。 */
  summarize: (options?: RequestOptions) =>
    getRequest('/scm/customer/visibility/customer-summary', {}, options) as unknown as Promise<ScmResponse<CustomerVisibilitySummary[]>>,
  query: (
    form: {
      pageNum: number;
      pageSize: number;
      customerId?: ScmId;
      skuId?: ScmId;
      visibilityPolicy?: string;
    },
    options?: RequestOptions
  ) => postRequest('/scm/customer/visibility/reverse/query', form, options) as unknown as Promise<ScmResponse<ScmPage<VisibilityRow>>>,
};
