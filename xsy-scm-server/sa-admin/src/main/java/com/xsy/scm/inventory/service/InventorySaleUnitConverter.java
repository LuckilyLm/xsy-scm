package com.xsy.scm.inventory.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.inventory.constant.InventoryErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** 订单销售数量与库存记账量的边界：只接受已冻结的精确换算，不四舍五入库存事实。 */
public final class InventorySaleUnitConverter {
    private InventorySaleUnitConverter() {
    }

    public static BigDecimal toStockQuantity(BigDecimal saleQuantity, String stockUnit, BigDecimal factor) {
        if (saleQuantity == null || saleQuantity.signum() < 0) {
            throw new ScmBusinessException(InventoryErrorCode.INVENTORY_UNIT_MISMATCH);
        }
        if (stockUnit == null && factor == null) {
            return saleQuantity;
        }
        if (stockUnit == null || stockUnit.isBlank() || factor == null || factor.signum() <= 0) {
            throw new ScmBusinessException(InventoryErrorCode.INVENTORY_UNIT_MISMATCH);
        }
        try {
            return saleQuantity.multiply(factor).setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ScmBusinessException(InventoryErrorCode.INVENTORY_UNIT_MISMATCH);
        }
    }

    /** 一笔出库流水还有多少库存量可退，按冻结比例向下折成销售单位；不超额退款。 */
    public static BigDecimal availableSaleQuantity(BigDecimal stockQuantity, String stockUnit, BigDecimal factor) {
        if (stockQuantity == null || stockQuantity.signum() < 0) {
            throw new ScmBusinessException(InventoryErrorCode.INVENTORY_UNIT_MISMATCH);
        }
        if (stockUnit == null && factor == null) {
            return stockQuantity;
        }
        if (stockUnit == null || stockUnit.isBlank() || factor == null || factor.signum() <= 0) {
            throw new ScmBusinessException(InventoryErrorCode.INVENTORY_UNIT_MISMATCH);
        }
        return stockQuantity.divide(factor, 4, RoundingMode.DOWN);
    }
}
