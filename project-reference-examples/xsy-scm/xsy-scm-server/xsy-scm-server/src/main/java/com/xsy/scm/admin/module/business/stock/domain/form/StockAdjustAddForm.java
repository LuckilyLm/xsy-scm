package com.xsy.scm.admin.module.business.stock.domain.form;

import com.xsy.scm.admin.module.business.stock.constant.AdjustTypeEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存调整单 添加表单（报损 / 报溢 / 盘点调整 / 规格转换）
 *
 * <p>调整数量与重量由表单传入；实际出入库流水与余额更新由库存业务层在审核完成时处理（本期骨架先落单，业务层后续接入）。</p>
 *
 * @author xsy-scm
 */
@Data
public class StockAdjustAddForm {

    @SchemaEnum(AdjustTypeEnum.class)
    @CheckEnum(message = "调整类型错误", value = AdjustTypeEnum.class, required = true)
    private Integer adjustType;

    @Schema(description = "商品ID")
    @NotNull(message = "商品不能为空")
    private Long productId;

    @Schema(description = "规格ID")
    @NotNull(message = "规格不能为空")
    private Long skuId;

    @Schema(description = "仓库ID，G-03 单仓库，字段保留备用")
    private Long warehouseId;

    @Schema(description = "调整数量（正数）")
    @NotNull(message = "调整数量不能为空")
    private BigDecimal quantity;

    @Schema(description = "调整重量（kg，正数）")
    @NotNull(message = "调整重量不能为空")
    private BigDecimal weight;

    @Schema(description = "调整原因")
    private String reason;
}
