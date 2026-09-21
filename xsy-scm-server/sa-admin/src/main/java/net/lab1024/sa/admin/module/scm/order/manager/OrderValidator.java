package net.lab1024.sa.admin.module.scm.order.manager;

import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings;

import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.*;

import java.math.BigDecimal;
import java.util.*;

public final class OrderValidator {
    private OrderValidator() {
    }

    public static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static void reason(String value, net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode code) {
        if (trim(value) == null) throw new ScmBusinessException(code);
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
    public static BigDecimal decimal(String value, boolean positive) {
        BigDecimal result;
        try {
            result = ScmDecimalStrings.parseScale4Required(value);
        } catch (ScmBusinessException e) {
            throw new ScmBusinessException(positive ? ORDER_QUANTITY_FORMAT_INVALID : ORDER_PRICE_INVALID);
        }
        if (positive && result.signum() <= 0) throw new ScmBusinessException(ORDER_QUANTITY_INVALID);
        return result;
    }

    public static void draft(SalesOrderAddForm f) {
        if (f.getItems() == null || f.getItems().isEmpty()) throw new ScmBusinessException(ORDER_QUANTITY_INVALID);
        if ("SUPPLEMENT".equals(f.getOrderSource())) reason(f.getSupplementReason(), ORDER_SUPPLEMENT_REASON_REQUIRED);
        else if (f.getOriginalOrderId() != null || trim(f.getSupplementReason()) != null)
            throw new ScmBusinessException(ORDER_SUPPLEMENT_INVALID);
        var seen = new HashSet<Long>();
        for (var x : f.getItems()) {
            if (x.getSkuId() == null || !seen.add(x.getSkuId())) throw new ScmBusinessException(ORDER_SKU_DUPLICATE);
            decimal(x.getOrderedQuantity(), true);
            if (Boolean.TRUE.equals(x.getManualPriceOverride())) {
                reason(x.getOverrideReason(), ORDER_PRICE_OVERRIDE_REASON_REQUIRED);
                if (x.getUnitPrice() == null) throw new ScmBusinessException(ORDER_PRICE_OVERRIDE_REASON_REQUIRED);
                decimal(x.getUnitPrice(), false);
            } else if (x.getUnitPrice() != null || trim(x.getOverrideReason()) != null)
                throw new ScmBusinessException(ORDER_PRICE_OVERRIDE_INVALID);
        }
    }
}
