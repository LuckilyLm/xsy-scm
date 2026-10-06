/**
 * 营销中心前端类型（与后端 promotion 域 VO 逐字对齐）。
 *
 * 金额一律是<b>四位定点字符串</b>，前端只渲染、不重算；优惠试算的金额与分摊都由服务端算好。
 */

export type Id = string | number;

export interface PromotionPage {
    pageNum: number;
    pageSize: number;
}

export type PromotionActivityType = 'FULL_REDUCE' | 'DISCOUNT' | 'FULL_GIFT' | 'SPECIAL_PRICE';

export type PromotionCouponDiscountType = 'AMOUNT' | 'RATE';

export type PromotionStatus = 'DRAFT' | 'ACTIVE' | 'STOPPED';

export type PromotionCouponInstanceStatus = 'AVAILABLE' | 'RESERVED' | 'USED' | 'RELEASED';

/**
 * 活动规则：<b>受控键值</b>，不是表达式。
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
    /** SPECIAL_PRICE：限时特价的 SKU 与特价单价。 */
    skuId?: string;
    specialPrice?: string;
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
    /** 行上的 SKU；限时特价按 SKU 命中。 */
    skuId: Id;
    /** 行数量（下单量）；限时特价让利 = 基础金额 − 数量 × 特价。 */
    quantity: string;
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
    /** 限时特价让利（作用于基础价之上、其余活动之前，按行归集）。 */
    specialDiscount?: string | null;
    activityDiscount?: string | null;
    couponDiscount?: string | null;
    discountAmount?: string | null;
    /** 客户实付 = 基础金额 − 合计优惠。 */
    finalAmount?: string | null;
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

/** 订单已冻结优惠的读模型（`OrderDiscountVO`，只读；冻结后不可变）。 */
export interface OrderDiscount {
    salesOrderId?: Id | null;
    /** 主活动（第一条产生优惠的活动）；叠加生效的其余活动在 `activitySnapshot.applied` 里。 */
    activityId?: Id | null;
    activityVersion?: number | null;
    activitySnapshot?: OrderDiscountActivitySnapshot | null;
    couponInstanceId?: Id | null;
    couponSnapshot?: OrderDiscountCouponSnapshot | null;
    /** 优惠基数（下单金额口径）。 */
    baseAmount?: string | null;
    discountAmount?: string | null;
    /** 其中的限时特价让利额（不得大于 discountAmount）。 */
    specialDiscountAmount?: string | null;
    allocations?: OrderDiscountAllocation[];
    roundingTargetItemId?: Id | null;
    createdAt?: string | null;
    createdBy?: string | null;
}

export interface OrderDiscountActivitySnapshot {
    /** 实际产生优惠的全部活动，按作用顺序；不同互斥组可叠加。 */
    applied?: OrderDiscountAppliedActivity[];
    /** 被互斥组挤掉的活动编码。 */
    suppressed?: string[];
}

export interface OrderDiscountAppliedActivity {
    activityId?: Id;
    activityCode?: string | null;
    activityName?: string | null;
    activityType?: string | null;
    version?: number | null;
    rule?: PromotionRule | null;
    discountAmount?: string | null;
}

export interface OrderDiscountCouponSnapshot {
    couponInstanceId?: Id;
    couponCode?: string | null;
    couponName?: string | null;
    couponDiscount?: string | null;
}

/** 逐行分摊：退款按这份分摊反向，不用退款时的当前活动重算。 */
export interface OrderDiscountAllocation {
    orderItemId?: Id;
    baseAmount?: string | null;
    discountAmount?: string | null;
}

/** 满赠赠品权益（`PromotionDiscountVO.GiftEntitlementVO`，冻结后不可改）。 */
export interface OrderGiftEntitlement {
    activityId?: Id;
    activityCode?: string | null;
    activityName?: string | null;
    version?: number | null;
    skuId?: Id;
    skuCode?: string | null;
    productName?: string | null;
    specName?: string | null;
    saleUnit?: string | null;
    quantity?: string | null;
    rule?: PromotionRule | null;
}

export const activityTypes: Record<PromotionActivityType, { label: string; color: string }> = {
    FULL_REDUCE: {label: '满减', color: 'red'},
    DISCOUNT: {label: '折扣', color: 'orange'},
    FULL_GIFT: {label: '满赠', color: 'purple'},
    SPECIAL_PRICE: {label: '限时特价', color: 'cyan'},
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

/**
 * 折扣率的<b>展示换算</b>：后端存的是 (0,1] 的比率（0.95），业务人员说的是百分比（95%）。
 *
 * 只在展示层换算，提交回来的比率与直接填比率完全一致 —— 后端与历史数据都不受影响。
 * 百分比取 2 位小数即可<b>无损</b>覆盖 4 位比率的全部取值（0.0001 比率 = 0.01%），
 * 因此往返 `rate → percent → rate` 不会丢精度。
 */
export function rateToPercent(rate?: string | null): number | null {
    if (rate === null || rate === undefined || rate === '') return null;
    const value = Number(rate);
    return Number.isFinite(value) ? Number((value * 100).toFixed(2)) : null;
}

/** {@link rateToPercent} 的逆运算；不填时返回 `undefined`（不收进规则载荷）。 */
export function percentToRate(percent?: number | null): string | undefined {
    return percent === null || percent === undefined ? undefined : (percent / 100).toFixed(4);
}
