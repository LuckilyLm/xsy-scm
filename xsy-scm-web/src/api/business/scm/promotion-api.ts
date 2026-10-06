/**
 * 营销中心接口。
 *
 * 试算是只读的（`POST` 只是因为要传订单行数组）：不占用券、不写任何表；
 * 只有 `confirm` 才占用券并冻结优惠。两者分开是 ADR-009 的明确要求 —— 预览不等于最终占用。
 */
import {getRequest, postRequest, request} from '/@/lib/axios';
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

/**
 * 带 `Idempotency-Key` 的命令通道：失败保留同一 UUID 供重试回放原结果，
 * 成功即释放，内容变化后换用新键。发券是「多张 INSERT」的写命令，重试不该多发一批券。
 */
const keys = new Map<string, string>();

async function promotionCommand<T>(path: string, data: unknown): Promise<ScmResponse<T>> {
    const signature = path + JSON.stringify(data);
    let key = keys.get(signature);
    if (!key) {
        key = crypto.randomUUID();
        keys.set(signature, key);
    }
    const result = (await request({
        url: path,
        method: 'post',
        data,
        headers: {'Idempotency-Key': key},
    })) as unknown as ScmResponse<T>;
    keys.delete(signature);
    return result;
}

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

    /** 启停：ACTIVE / STOPPED。券新建后是草稿，只有生效中的券才允许发出。 */
    couponStatus: (id: Id, version: number, status: 'ACTIVE' | 'STOPPED') =>
        postRequest(`/scm/promotion/coupon/${id}/status`, {version, status}) as unknown as Promise<
            ScmResponse<string>
        >,

    /** 发券：带 `Idempotency-Key`，重试回放首次结果，不重复发券。 */
    couponIssue: (couponId: Id, customerId: Id, quantity: number) =>
        promotionCommand<number>('/scm/promotion/coupon/issue', {couponId, customerId, quantity}),

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

    /**
     * 冻结不再有独立端点：优惠由订单确认在服务端按订单事实冻结。
     *
     * 客户端只传 `couponInstanceId`（客户选用哪张券是客户的权益），活动由服务端自行选出，
     * 客户与行金额由服务端从订单读取 —— 这样不会出现「订单确认了但优惠没冻结」的中间态。
     * 因此这里刻意没有 `discountConfirm`：一个不存在的函数比一个会返回 404 的函数更说明问题。
     */
};

export default promotionApi;
