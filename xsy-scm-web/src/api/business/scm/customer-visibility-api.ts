import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {CustomerVisibilityPolicy, ScmId, ScmPage} from '/@/types/business/scm/customer';
import type {CustomerVisibilitySummary, VisibilityRow} from '/@/types/business/scm/pricing';
export const customerVisibilityApi = {
  /** 客户详情页调整可售商品：策略与授权规格整体替换。 */
  replace: (
    form: {
      customerId: ScmId;
      visibilityPolicy: CustomerVisibilityPolicy;
      visibilities: Array<{skuId: ScmId; id?: number; version?: number}>;
    },
    options?: RequestOptions
  ) => postRequest<string>('/scm/customer/visibility/replace', form, options),
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
