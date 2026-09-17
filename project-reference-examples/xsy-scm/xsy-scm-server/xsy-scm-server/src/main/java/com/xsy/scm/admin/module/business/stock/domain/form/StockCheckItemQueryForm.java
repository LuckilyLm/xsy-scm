package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 库存盘点明细 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class StockCheckItemQueryForm extends PageParam {

    @Schema(description = "盘点单ID，用于定位明细")
    @NotNull(message = "盘点单ID不能为空")
    private Long checkId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
