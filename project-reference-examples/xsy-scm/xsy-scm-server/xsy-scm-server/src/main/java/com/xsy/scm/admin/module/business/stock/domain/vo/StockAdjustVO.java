package com.xsy.scm.admin.module.business.stock.domain.vo;

import com.xsy.scm.admin.module.business.stock.constant.AdjustStatusEnum;
import com.xsy.scm.admin.module.business.stock.constant.AdjustTypeEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存调整单 返回对象
 *
 * @author xsy-scm
 */
@Data
public class StockAdjustVO {

    @Schema(description = "调整单ID")
    private Long adjustId;

    @Schema(description = "调整单号")
    private String adjustNo;

    @SchemaEnum(AdjustTypeEnum.class)
    private Integer adjustType;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "仓库ID")
    private Long warehouseId;

    @Schema(description = "调整数量（正数）")
    private BigDecimal quantity;

    @Schema(description = "调整重量（kg，正数）")
    private BigDecimal weight;

    @Schema(description = "调整原因")
    private String reason;

    @SchemaEnum(AdjustStatusEnum.class)
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
