package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 商品价格 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ProductPriceQueryForm extends PageParam {

    @Schema(description = "商品ID，用于定位价格")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "价格类型：1 基础价，2 客户分级价，3 时价，4 协议价")
    private Integer priceType;

    @Schema(description = "状态：1 生效，2 失效")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
