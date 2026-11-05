package com.xsy.scm.pricing.manager;

import com.xsy.scm.pricing.constant.AgreementFormulaType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AgreementFormulaCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private AgreementFormulaCalculator() {
    }

    public static BigDecimal calculate(AgreementFormulaType type, BigDecimal fixedPrice, BigDecimal parameter,
        BigDecimal marketPrice, BigDecimal saleUnitCost) {
        return calculate(type, fixedPrice, parameter, marketPrice, saleUnitCost, null, null, null);
    }

    public static BigDecimal calculate(AgreementFormulaType type, BigDecimal fixedPrice, BigDecimal parameter,
        BigDecimal marketPrice, BigDecimal saleUnitCost, BigDecimal latestPurchasePrice, BigDecimal lossRate,
        BigDecimal taxRate) {
        // 导入的配置有误时，不能悄悄变成折扣，也不能让定价读取路径抛异常。
        if (type == null || (type != AgreementFormulaType.FIXED && (parameter == null || parameter.signum() < 0))) {
            return null;
        }
        BigDecimal value = switch (type) {
            case FIXED -> fixedPrice;
            case MARKET_PLUS_AMOUNT -> marketPrice == null ? null : marketPrice.add(parameter);
            case COST_MARKUP_RATE -> saleUnitCost == null ? null : saleUnitCost.multiply(BigDecimal.ONE.add(parameter));
            case MARKET_MARKUP_RATE -> marketPrice == null ? null : marketPrice.multiply(BigDecimal.ONE.add(parameter));
            case PURCHASE_COST_LOSS_TAX_MARKUP ->
                purchaseCostLossTaxMarkup(parameter, saleUnitCost, latestPurchasePrice, lossRate, taxRate);
        };
        if (value == null || value.signum() < 0 || value.precision() - value.scale() > 14) {
            return null;
        }
        BigDecimal rounded = value.setScale(4, RoundingMode.HALF_UP);
        return rounded.precision() - rounded.scale() > 14 ? null : rounded;
    }

    /**
     * 损耗率与税率以百分数存储（例如 5.0000 表示 5%），计算时换算成系数； 任一比率缺失都视为不可定价，不以 0 代替。
     */
    private static BigDecimal purchaseCostLossTaxMarkup(BigDecimal parameter, BigDecimal saleUnitCost,
        BigDecimal latestPurchasePrice, BigDecimal lossRate, BigDecimal taxRate) {
        if (lossRate == null || taxRate == null || lossRate.signum() < 0 || taxRate.signum() < 0
            || lossRate.compareTo(HUNDRED) > 0 || taxRate.compareTo(HUNDRED) > 0) {
            return null;
        }
        BigDecimal base = latestPurchasePrice != null ? latestPurchasePrice : saleUnitCost;
        if (base == null) {
            return null;
        }
        return base.multiply(BigDecimal.ONE.add(lossRate.movePointLeft(2)))
            .multiply(BigDecimal.ONE.add(taxRate.movePointLeft(2))).multiply(BigDecimal.ONE.add(parameter));
    }
}
