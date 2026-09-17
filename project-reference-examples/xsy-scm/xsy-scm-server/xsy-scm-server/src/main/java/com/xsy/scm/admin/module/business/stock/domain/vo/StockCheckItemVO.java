package com.xsy.scm.admin.module.business.stock.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存盘点明细 返回对象
 *
 * @author xsy-scm
 */
@Data
public class StockCheckItemVO {

    @Schema(description = "明细ID")
    private Long itemId;

    @Schema(description = "盘点单ID")
    private Long checkId;

    @Schema(description = "商品ID")
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

    @Schema(description = "差异数量（实盘 - 账面）")
    private BigDecimal diffQuantity;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
