package com.xsy.scm.order.manager;


import com.xsy.scm.order.domain.form.SalesOrderAddForm;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_PRICE_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_PRICE_OVERRIDE_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_PRICE_OVERRIDE_REASON_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_QUANTITY_FORMAT_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_QUANTITY_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_SKU_DUPLICATE;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_SUPPLEMENT_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_SUPPLEMENT_REASON_REQUIRED;

import java.math.BigDecimal;
import java.util.HashSet;

public final class OrderValidator {
    private OrderValidator() {
    }

    public static String trim(String inputText) {
        return inputText == null || inputText.isBlank() ? null : inputText.trim();
    }

    public static void reason(String reasonText, com.xsy.scm.common.error.ScmErrorCode code) {
        if (trim(reasonText) == null) throw new ScmBusinessException(code);
    }

    /**
     * 解析数量 / 金额的定点字符串。
     *
     * <p>形态规则直接复用 {@link ScmDecimalStrings}：订单域曾要求「恰好 4 位小数」，比全项目唯一规则
     * 更严，导致前端与 Excel 导入提交的 {@code "10"} 被拒。负数、科学计数法与超 4 位小数仍然拒绝。
     *
     * @param positive {@code true} = 数量（必须 &gt; 0）；{@code false} = 单价（允许 0）
     * @return 4 位小数的 {@link BigDecimal}，与 {@code NUMERIC(18,4)} 及对外序列化形态一致
     */
    public static BigDecimal decimal(String decimalText, boolean positive) {
        BigDecimal result;
        try {
            result = ScmDecimalStrings.parseScale4Required(decimalText);
        } catch (ScmBusinessException e) {
            throw new ScmBusinessException(positive ? ORDER_QUANTITY_FORMAT_INVALID : ORDER_PRICE_INVALID);
        }
        if (positive && result.signum() <= 0) throw new ScmBusinessException(ORDER_QUANTITY_INVALID);
        return result;
    }

    public static void draft(SalesOrderAddForm salesOrderForm) {
        if (salesOrderForm.getItems() == null
            || salesOrderForm.getItems().isEmpty()) throw new ScmBusinessException(ORDER_QUANTITY_INVALID);
        if (ScmOrderSourceEnum.SUPPLEMENT.name().equals(salesOrderForm.getOrderSource())) {
            reason(salesOrderForm.getSupplementReason(), ORDER_SUPPLEMENT_REASON_REQUIRED);
        }
        else if (salesOrderForm.getOriginalOrderId() != null || trim(salesOrderForm.getSupplementReason()) != null)
            throw new ScmBusinessException(ORDER_SUPPLEMENT_INVALID);
        var seen = new HashSet<Long>();
        for (var orderItemForm : salesOrderForm.getItems()) {
            if (orderItemForm.getSkuId() == null || !seen.add(orderItemForm.getSkuId())) {
                throw new ScmBusinessException(ORDER_SKU_DUPLICATE);
            }
            decimal(orderItemForm.getOrderedQuantity(), true);
            if (Boolean.TRUE.equals(orderItemForm.getManualPriceOverride())) {
                reason(orderItemForm.getOverrideReason(), ORDER_PRICE_OVERRIDE_REASON_REQUIRED);
                if (orderItemForm.getUnitPrice() == null) {
                    throw new ScmBusinessException(ORDER_PRICE_OVERRIDE_REASON_REQUIRED);
                }
                decimal(orderItemForm.getUnitPrice(), false);
            } else if (orderItemForm.getUnitPrice() != null || trim(orderItemForm.getOverrideReason()) != null)
                throw new ScmBusinessException(ORDER_PRICE_OVERRIDE_INVALID);
        }
    }
}
