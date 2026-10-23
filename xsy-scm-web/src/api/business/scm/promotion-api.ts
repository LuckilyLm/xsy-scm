/** 营销中心接口。试算只读，不占券不写表；只有确认订单才占用优惠。 */
import {getRequest, postRequest, request, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
  Id,
  PromotionActivity,
  PromotionActivityQuery,
  PromotionActivitySave,
  PromotionCoupon,
  PromotionCouponInstance,
  PromotionCouponQuery,
  PromotionCouponSave,
  PromotionDiscount,
  PromotionDiscountLine,
} from '/@/views/business/scm/promotion/promotion-types';

/** 幂等命令通道：失败保留同一 UUID 供重试，成功即释放，内容变化后换新键。 */
const keys = new Map<string, string>();

async function promotionCommand<T>(path: string, data: unknown): Promise<ScmResponse<T>> {
  const signature = path + JSON.stringify(data);
  let key = keys.get(signature);
  if (!key) {
    key = crypto.randomUUID();
    keys.set(signature, key);
  }
  const result = await request<T>({
    url: path,
    method: 'post',
    data,
    headers: {'Idempotency-Key': key},
  });
  keys.delete(signature);
  return result;
}

export const promotionApi = {
  // ---- 活动 ----
  activityQuery: (data: PromotionActivityQuery, options?: RequestOptions) =>
    postRequest<ScmPage<PromotionActivity>>('/scm/promotion/activity/query', data, options),

  activityDetail: (id: Id) => getRequest<PromotionActivity>(`/scm/promotion/activity/${id}`, {}),

  activitySave: (data: PromotionActivitySave) => postRequest<Id>('/scm/promotion/activity/save', data),

  /** 启停：ACTIVE / STOPPED。生效中的活动不能改内容，只能先停用。 */
  activityStatus: (id: Id, version: number, status: 'ACTIVE' | 'STOPPED') =>
    postRequest<string>(`/scm/promotion/activity/${id}/status`, {version, status}),

  // ---- 优惠券 ----
  couponQuery: (data: PromotionCouponQuery, options?: RequestOptions) =>
    postRequest<ScmPage<PromotionCoupon>>('/scm/promotion/coupon/query', data, options),

  couponDetail: (id: Id) => getRequest<PromotionCoupon>(`/scm/promotion/coupon/${id}`, {}),

  couponSave: (data: PromotionCouponSave) => postRequest<Id>('/scm/promotion/coupon/save', data),

  /** 启停：ACTIVE / STOPPED。券新建后是草稿，只有生效中的券才允许发出。 */
  couponStatus: (id: Id, version: number, status: 'ACTIVE' | 'STOPPED') =>
    postRequest<string>(`/scm/promotion/coupon/${id}/status`, {version, status}),

  /** 发券：带 `Idempotency-Key`，重试回放首次结果，不重复发券。 */
  couponIssue: (couponId: Id, customerId: Id, quantity: number) =>
    promotionCommand<number>('/scm/promotion/coupon/issue', {couponId, customerId, quantity}),

  couponInstances: (customerId: Id, status?: string, options?: RequestOptions) =>
    getRequest<PromotionCouponInstance[]>('/scm/promotion/coupon/instances', status ? {customerId, status} : {customerId}, options),

  // ---- 优惠试算 / 冻结 ----
  discountPreview: (
    form: {
      customerId: Id;
      activityId?: Id | null;
      couponInstanceId?: Id | null;
      lines: PromotionDiscountLine[];
    },
    options?: RequestOptions
  ) => postRequest<PromotionDiscount>('/scm/promotion/discount/preview', form, options),

  /** 优惠冻结属于服务端订单确认事务，前端只选择优惠券，不单独提交活动或调用冻结接口。 */
};

export default promotionApi;
