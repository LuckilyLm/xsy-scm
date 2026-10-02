/**
 * 营销中心接口。
 *
 * 试算是只读的（`POST` 只是因为要传订单行数组）：**不占用券、不写任何表**；
 * 只有 `confirm` 才占用券并冻结优惠。两者分开是 ADR-009 的明确要求 —— 预览不等于最终占用。
 */
import {getRequest, postRequest} from '/@/lib/axios';
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

export const promotionApi = {
    // ---- 活动 ----
    activityQuery: (data: PromotionActivityQuery) =>
        postRequest('/scm/promotion/activity/query', data) as unknown as Promise<
            ScmResponse<ScmPage<PromotionActivity>>
        >,

    activityDetail: (id: Id) =>
        getRequest(`/scm/promotion/activity/${id}`, {}) as unknown as Promise<ScmResponse<PromotionActivity>>,

    activitySave: (data: PromotionActivitySave) =>
        postRequest('/scm/promotion/activity/save', data) as unknown as Promise<ScmResponse<Id>>,

    /** 启停：ACTIVE / STOPPED。生效中的活动不能改内容，只能先停用。 */
    activityStatus: (id: Id, version: number, status: 'ACTIVE' | 'STOPPED') =>
        postRequest(`/scm/promotion/activity/${id}/status`, {version, status}) as unknown as Promise<
            ScmResponse<string>
        >,

    // ---- 优惠券 ----
    couponQuery: (data: PromotionCouponQuery) =>
        postRequest('/scm/promotion/coupon/query', data) as unknown as Promise<ScmResponse<ScmPage<PromotionCoupon>>>,

    couponDetail: (id: Id) =>
        getRequest(`/scm/promotion/coupon/${id}`, {}) as unknown as Promise<ScmResponse<PromotionCoupon>>,

    couponSave: (data: PromotionCouponSave) =>
        postRequest('/scm/promotion/coupon/save', data) as unknown as Promise<ScmResponse<Id>>,

    couponIssue: (couponId: Id, customerId: Id, quantity: number) =>
        postRequest('/scm/promotion/coupon/issue', {couponId, customerId, quantity}) as unknown as Promise<
            ScmResponse<number>
        >,

    couponInstances: (customerId: Id, status?: string) =>
        getRequest('/scm/promotion/coupon/instances', status ? {customerId, status} : {customerId}) as unknown as Promise<
            ScmResponse<PromotionCouponInstance[]>
        >,

    // ---- 优惠试算 / 冻结 ----
    discountPreview: (form: {
        customerId: Id;
        activityId?: Id | null;
        couponInstanceId?: Id | null;
        lines: PromotionDiscountLine[];
    }) =>
        postRequest('/scm/promotion/discount/preview', form) as unknown as Promise<ScmResponse<PromotionDiscount>>,

    /** 冻结：占用券并写入订单优惠快照；服务端重验活动与券的当前状态。 */
    discountConfirm: (form: {
        salesOrderId: Id;
        customerId: Id;
        activityId?: Id | null;
        couponInstanceId?: Id | null;
        lines: PromotionDiscountLine[];
    }) =>
        postRequest('/scm/promotion/discount/confirm', form) as unknown as Promise<ScmResponse<PromotionDiscount>>,
};

export default promotionApi;
