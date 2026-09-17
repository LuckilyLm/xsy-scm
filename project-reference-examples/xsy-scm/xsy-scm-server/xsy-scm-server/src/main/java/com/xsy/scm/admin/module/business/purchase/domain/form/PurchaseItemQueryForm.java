package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 采购明细 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class PurchaseItemQueryForm extends PageParam {

    @Schema(description = "采购单ID，用于定位明细")
    @NotNull(message = "采购单ID不能为空")
    private Long purchaseId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "绑定供应商ID")
    private Long supplierId;

    @Schema(description = "状态：1 待收，2 部分收，3 已收齐")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
