/**
 * 营销中心前端类型（与后端 promotion 域 VO 逐字对齐）。
 *
 * 金额一律是**四位定点字符串**，前端只渲染、不重算；优惠试算的金额与分摊都由服务端算好。
 */

export type Id = string | number;

export interface PromotionPage {
    pageNum: number;
    pageSize: number;
}

export type PromotionActivityType = 'FULL_REDUCE' | 'DISCOUNT' | 'FULL_GIFT';

export type PromotionCouponDiscountType = 'AMOUNT' | 'RATE';

export type PromotionStatus = 'DRAFT' | 'ACTIVE' | 'STOPPED';

export type PromotionCouponInstanceStatus = 'AVAILABLE' | 'RESERVED' | 'USED' | 'RELEASED';

/**
 * 活动规则：**受控键值**，不是表达式。
 *
 * 每种活动类型只接受自己的键，未知键会被服务端拒收（41324）——
 * 因此这里的字段按类型分开声明，不合并成一个「任意键」的字典。
 */
export interface PromotionRule {
    /** FULL_REDUCE / FULL_GIFT：门槛金额。 */
    thresholdAmount?: string;
    /** FULL_REDUCE：减免金额。 */
    reduceAmount?: string;
    /** DISCOUNT：折扣率，例如 0.95。 */
    discountRate?: string;
    /** FULL_GIFT：赠品 SKU 与数量。 */
    giftSkuId?: string;
    giftQuantity?: string;
}

export interface PromotionActivity {
    id: Id;
    activityCode: string;
    activityName: string;
    activityType: PromotionActivityType;
    activityTypeLabel?: string;
    exclusiveGroup?: string | null;
    priority: number;
    validFrom: string;
    validTo: string;
    status: PromotionStatus;
    rule: PromotionRule;
    remark?: string | null;
    version: number;
}

export interface PromotionActivityQuery extends PromotionPage {
    keyword?: string;
    activityType?: PromotionActivityType;
    status?: PromotionStatus;
}

export interface PromotionActivitySave {
    id?: Id;
    activityCode: string;
    activityName: string;
    activityType: PromotionActivityType;
    exclusiveGroup?: string | null;
    priority: number;
    validFrom: string;
    validTo: string;
    rule: PromotionRule;
    remark?: string | null;
    version?: number;
}

export interface PromotionCoupon {
    id: Id;
    couponCode: string;
    couponName: string;
    discountType: PromotionCouponDiscountType;
    discountTypeLabel?: string;
    discountValue?: string | null;
    minOrderAmount?: string | null;
    validFrom: string;
    validTo: string;
    status: PromotionStatus;
    remark?: string | null;
    version: number;
}

export interface PromotionCouponQuery extends PromotionPage {
    keyword?: string;
    status?: PromotionStatus;
}

export interface PromotionCouponSave {
    id?: Id;
    couponCode: string;
    couponName: string;
    discountType: PromotionCouponDiscountType;
    discountValue: string;
    minOrderAmount?: string | null;
    validFrom: string;
    validTo: string;
    remark?: string | null;
    version?: number;
}

/** 客户持有的券。 */
export interface PromotionCouponInstance {
    id: Id;
    couponId: Id;
    couponCode?: string | null;
    couponName?: string | null;
    customerId: Id;
    instanceNo: string;
    status: PromotionCouponInstanceStatus;
    reservedOrderId?: Id | null;
    reservedAt?: string | null;
    usedOrderId?: Id | null;
    usedAt?: string | null;
    releasedAt?: string | null;
    releaseReason?: string | null;
    version: number;
}

export interface PromotionDiscountLine {
    orderItemId: Id;
    baseAmount: string;
}

export interface PromotionDiscountAllocation {
    orderItemId: Id;
    baseAmount?: string | null;
    discountAmount?: string | null;
}

/** 试算 / 冻结结果：三个金额分开返回，便于回答「券到底减了多少」。 */
export interface PromotionDiscount {
    salesOrderId?: Id | null;
    customerId: Id;
    baseAmount?: string | null;
    activityDiscount?: string | null;
    couponDiscount?: string | null;
    discountAmount?: string | null;
    activityId?: Id | null;
    activityCode?: string | null;
    activityName?: string | null;
    activityVersion?: number | null;
    couponInstanceId?: Id | null;
    couponCode?: string | null;
    couponName?: string | null;
    allocations: PromotionDiscountAllocation[];
    roundingTargetItemId?: Id | null;
    suppressedActivities?: string[];
    frozen?: boolean;
    activityRule?: PromotionRule | null;
}

export const activityTypes: Record<PromotionActivityType, { label: string; color: string }> = {
    FULL_REDUCE: {label: '满减', color: 'red'},
    DISCOUNT: {label: '折扣', color: 'orange'},
    FULL_GIFT: {label: '满赠', color: 'purple'},
};

export const promotionStatuses: Record<PromotionStatus, { label: string; color: string }> = {
    DRAFT: {label: '草稿', color: 'default'},
    ACTIVE: {label: '生效中', color: 'green'},
    STOPPED: {label: '已停用', color: 'default'},
};

export const couponInstanceStatuses: Record<PromotionCouponInstanceStatus, { label: string; color: string }> = {
    AVAILABLE: {label: '可用', color: 'green'},
    RESERVED: {label: '已占用', color: 'blue'},
    USED: {label: '已核销', color: 'default'},
    RELEASED: {label: '已释放', color: 'orange'},
};

export function promotionError(error: unknown): string {
    const response = error as { data?: { msg?: string }; response?: { data?: { msg?: string } }; message?: string };
    return response?.data?.msg ?? response?.response?.data?.msg ?? response?.message ?? '操作失败，请刷新后重试';
}
