import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmId, ScmPage} from '/@/types/business/scm/customer';
import type {CustomerVisibilitySummary, VisibilityRow} from '/@/types/business/scm/pricing';
export const customerVisibilityApi = {
  /** 按客户汇总可见商品与规格数，列表页的客户卡片使用。 */
  summarize: (options?: RequestOptions) => getRequest<CustomerVisibilitySummary[]>('/scm/customer/visibility/customer-summary', {}, options),
  query: (
    form: {
      pageNum: number;
      pageSize: number;
      customerId?: ScmId;
      skuId?: ScmId;
      visibilityPolicy?: string;
    },
    options?: RequestOptions
  ) => postRequest<ScmPage<VisibilityRow>>('/scm/customer/visibility/reverse/query', form, options),
};
