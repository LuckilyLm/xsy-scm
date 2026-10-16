package com.xsy.scm.mall.catalog.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import java.math.BigDecimal;

/** 商城数量按商品规格的销售单位约束，服务端购物车与结算共用。 */
public final class MallQuantityPolicy {
    private MallQuantityPolicy() {
    }

    public static boolean accepts(MallProductVO product, BigDecimal quantity) {
        if (product == null || quantity == null)
            return false;
        BigDecimal minimum = product.getMinOrderQty() == null ? BigDecimal.ONE : product.getMinOrderQty();
        BigDecimal step = product.getStepQty() == null ? BigDecimal.ONE : product.getStepQty();
        return minimum.signum() > 0 && step.signum() > 0 && quantity.compareTo(minimum) >= 0
                && quantity.subtract(minimum).remainder(step).signum() == 0;
    }

    public static void require(MallProductVO product, BigDecimal quantity) {
        if (!accepts(product, quantity)) {
            throw new ScmBusinessException(MallErrorCode.CART_QUANTITY_RULE_INVALID);
        }
    }
}
