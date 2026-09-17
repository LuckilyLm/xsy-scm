package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户折扣率 添加表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerDiscountAddForm {

    @Schema(description = "客户ID")
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @Schema(description = "折扣范围：1 统一折扣，2 按商品，3 按分类")
    @NotNull(message = "折扣范围不能为空")
    private Integer scopeType;

    @Schema(description = "商品ID（scopeType=2 时使用）")
    private Long productId;

    @Schema(description = "分类ID（scopeType=3 时使用）")
    private Long categoryId;

    @Schema(description = "折扣率（0~1，1 表示不打折）")
    @NotNull(message = "折扣率不能为空")
    private BigDecimal discountRate;

    @Schema(description = "状态：1 生效，2 停用")
    private Integer status;
}
