package com.xsy.scm.promotion.support;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.promotion.constant.PromotionErrorCode;
import com.xsy.scm.promotion.constant.ScmPromotionActivityTypeEnum;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 活动规则校验：<b>受控规则的唯一准入点</b>。
 *
 * <p>
 * 与打印模板同一取向：规则是一组具名键值，不是表达式，也不是「什么键都收下、算的时候再挑」。 未知键一律拒收 —— 宽容会让一份写错的规则静默变成另一条活动，而用户以为它生效了。
 *
 * <p>
 * 校验通过后返回<b>归一化</b>的规则（去空白、金额定标到 4 位），冻结进订单优惠的就是这一份。
 */
public final class PromotionRuleValidator {

    private static final int SCALE = 4;

    private PromotionRuleValidator() {
    }

    /**
     * 按活动类型校验并归一化规则。
     */
    public static Map<String, Object> validate(ScmPromotionActivityTypeEnum type, Map<String, Object> rule) {
        if (rule == null) {
            throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        switch (type) {
            case FULL_REDUCE -> {
                requireOnly(rule, "thresholdAmount", "reduceAmount");
                BigDecimal threshold = positive(rule.get("thresholdAmount"));
                BigDecimal reduce = positive(rule.get("reduceAmount"));
                if (reduce.compareTo(threshold) > 0) {
                    // 满 100 减 200 在数学上成立，在业务上是配置错误，直接拒收
                    throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
                }
                normalized.put("thresholdAmount", threshold.toPlainString());
                normalized.put("reduceAmount", reduce.toPlainString());
            }
            case DISCOUNT -> {
                requireOnly(rule, "discountRate");
                BigDecimal rate = decimal(rule.get("discountRate"));
                if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                    throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
                }
                normalized.put("discountRate", rate.setScale(SCALE, java.math.RoundingMode.HALF_UP).toPlainString());
            }
            case FULL_GIFT -> {
                requireOnly(rule, "thresholdAmount", "giftSkuId", "giftQuantity");
                normalized.put("thresholdAmount", positive(rule.get("thresholdAmount")).toPlainString());
                normalized.put("giftSkuId", String.valueOf(requiredLong(rule.get("giftSkuId"))));
                normalized.put("giftQuantity", positive(rule.get("giftQuantity")).toPlainString());
            }
            case SPECIAL_PRICE -> {
                requireOnly(rule, "skuId", "specialPrice");
                normalized.put("skuId", String.valueOf(requiredLong(rule.get("skuId"))));
                // 特价必须为正：0 元特价等于白送，属于赠品而不是特价，走满赠那条链
                normalized.put("specialPrice", positive(rule.get("specialPrice")).toPlainString());
            }
            default -> throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
        }
        return normalized;
    }

    /**
     * 只接受声明的键：未知键拒收（见类说明）。
     */
    private static void requireOnly(Map<String, Object> rule, String... allowed) {
        for (String key : rule.keySet()) {
            boolean known = false;
            for (String candidate : allowed) {
                if (candidate.equals(key)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
            }
        }
        for (String key : allowed) {
            if (rule.get(key) == null) {
                throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
            }
        }
    }

    private static BigDecimal positive(Object value) {
        BigDecimal decimal = decimal(value);
        if (decimal.signum() <= 0) {
            throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
        }
        return decimal.setScale(SCALE, java.math.RoundingMode.HALF_UP);
    }

    private static long requiredLong(Object value) {
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
        }
    }

    private static BigDecimal decimal(Object value) {
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
        }
    }
}
