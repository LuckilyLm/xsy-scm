package com.xsy.scm.admin.module.business.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购大屏 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ScreenPurchaseVO {

    @Schema(description = "采购单数")
    private Long purchaseCount;

    @Schema(description = "采购金额（不含税）")
    private BigDecimal purchaseAmount;

    @Schema(description = "供应商数")
    private Long supplierCount;
}
