package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存盘点明细 添加表单
 *
 * @author xsy-scm
 */
@Data
public class StockCheckItemAddForm {

    @Schema(description = "盘点单ID")
    @NotNull(message = "盘点单ID不能为空")
    private Long checkId;

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "账面数量")
    private BigDecimal bookQuantity;

    @Schema(description = "账面重量（kg）")
    private BigDecimal bookWeight;

    @Schema(description = "实盘数量")
    private BigDecimal actualQuantity;

    @Schema(description = "实盘重量（kg）")
    private BigDecimal actualWeight;
}
