package com.xsy.scm.admin.module.business.order.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 销售订单明细 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class SaleOrderItemQueryForm extends PageParam {

    @Schema(description = "订单ID，用于定位明细")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "明细状态：1 正常，2 已退款，3 已退货")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
