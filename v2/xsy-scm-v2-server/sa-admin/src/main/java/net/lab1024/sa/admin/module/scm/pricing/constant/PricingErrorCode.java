package net.lab1024.sa.admin.module.scm.pricing.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;
@Getter @RequiredArgsConstructor
public enum PricingErrorCode implements ScmErrorCode {
    PRICE_INVALID(40030,"价格必须为非负四位定点数"),
    PERIOD_INVALID(40031,"结束时间必须晚于开始时间"),
    AGREEMENT_PRICE_NOT_FOUND(40432,"协议价不存在"),
    CUSTOMER_TYPE_PRICE_NOT_FOUND(40433,"客户类型价不存在"),
    AGREEMENT_PRICE_OVERLAP(40933,"协议价有效期重叠"),
    CUSTOMER_TYPE_PRICE_OVERLAP(40935,"客户类型价有效期重叠"),
    PRICE_BATCH_ROW_INVALID(40035,"批量调价存在非法行"),
    PRICE_BATCH_KEY_DUPLICATE(40948,"批次号已成功提交，请勿重复提交"),
    SKU_NOT_SELLABLE(40949,"SKU 不可售、不可见或未定价"),
    PRICE_RESOLVE_CUSTOMER_TYPE_MISSING(40036,"客户未设置有效的客户类型");
    private final int code;
    private final String msg;
}
