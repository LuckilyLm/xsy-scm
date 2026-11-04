package com.xsy.scm.pricing.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

@Getter
@RequiredArgsConstructor
public enum PricingErrorCode implements ScmErrorCode {
    PRICE_INVALID(40030, "价格不能小于0，最多保留4位小数"),
    PERIOD_INVALID(40031, "结束时间必须晚于开始时间"),
    AGREEMENT_PRICE_NOT_FOUND(40432, "协议价不存在"),
    GROUP_PRICE_NOT_FOUND(40436, "客户分组价不存在"),
    GROUP_PRICE_INVALID(40043, "客户分组价必须指定商品、销售单位与非负价格"),
    GROUP_PRICE_OVERLAP(40956, "同一客户分组、商品与销售单位的价格区间不能重叠"),
    AGREEMENT_PRICE_OVERLAP(40933, "协议价有效期重叠"),
    SKU_NOT_SELLABLE(40949, "商品规格不可售、不可见或未定价"),
    TIME_PRICE_NOT_FOUND(40050, "时价记录不存在"),

    TIME_PRICE_WINDOW_INVALID(40051, "生效结束时间必须晚于开始时间"),

    TIME_PRICE_OVERLAP(40052, "同一商品规格的时价区间不能重叠"),

    TIME_PRICE_UPDATE_CONFLICT(40053, "时价已被他人修改，请刷新后重试");
    private final int code;
    private final String msg;
}
