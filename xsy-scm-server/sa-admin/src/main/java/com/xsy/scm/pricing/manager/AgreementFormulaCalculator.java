package com.xsy.scm.pricing.manager;

import com.xsy.scm.pricing.constant.AgreementFormulaType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AgreementFormulaCalculator {
    private AgreementFormulaCalculator() {
    }

    public static BigDecimal calculate(AgreementFormulaType type, BigDecimal fixedPrice, BigDecimal parameter,
        BigDecimal marketPrice, BigDecimal saleUnitCost) {
        // 导入的配置有误时，不能悄悄变成折扣，也不能让定价读取路径抛异常。
        if (type == null || (type != AgreementFormulaType.FIXED && (parameter == null || parameter.signum() < 0))) {
            return null;
        }
        BigDecimal value = switch (type) {
            case FIXED -> fixedPrice;
            case MARKET_PLUS_AMOUNT -> marketPrice == null ? null : marketPrice.add(parameter);
            case COST_MARKUP_RATE -> saleUnitCost == null ? null : saleUnitCost.multiply(BigDecimal.ONE.add(parameter));
            case MARKET_MARKUP_RATE -> marketPrice == null ? null : marketPrice.multiply(BigDecimal.ONE.add(parameter));
        };
        if (value == null || value.signum() < 0 || value.precision() - value.scale() > 14) {
            return null;
        }
        BigDecimal rounded = value.setScale(4, RoundingMode.HALF_UP);
        return rounded.precision() - rounded.scale() > 14 ? null : rounded;
    }
}
