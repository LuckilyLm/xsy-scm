package com.xsy.scm.pricing.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

@Getter
@RequiredArgsConstructor
public enum PricingErrorCode implements ScmErrorCode {
    PRICE_INVALID(40030, "价格必须为非负四位定点数"),
    PERIOD_INVALID(40031, "结束时间必须晚于开始时间"),
    AGREEMENT_PRICE_NOT_FOUND(40432, "协议价不存在"),
    CUSTOMER_TYPE_PRICE_NOT_FOUND(40433, "客户类型价不存在"),
    AGREEMENT_PRICE_OVERLAP(40933, "协议价有效期重叠"),
    CUSTOMER_TYPE_PRICE_OVERLAP(40935, "客户类型价有效期重叠"),
    PRICE_BATCH_ROW_INVALID(40035, "批量调价存在非法行"),
    PRICE_BATCH_KEY_DUPLICATE(40948, "批次号已成功提交，请勿重复提交"),
    SKU_NOT_SELLABLE(40949, "商品规格不可售、不可见或未定价"),
    PRICE_RESOLVE_CUSTOMER_TYPE_MISSING(40036, "客户未设置有效的客户类型"),
    TIME_PRICE_NOT_FOUND(40050, "时价记录不存在"),

    TIME_PRICE_WINDOW_INVALID(40051, "生效结束时间必须晚于开始时间"),

    TIME_PRICE_OVERLAP(40052, "同一商品规格的时价区间不能重叠"),

    TIME_PRICE_UPDATE_CONFLICT(40053, "时价已被他人修改，请刷新后重试");
    private final int code;
    private final String msg;
}
