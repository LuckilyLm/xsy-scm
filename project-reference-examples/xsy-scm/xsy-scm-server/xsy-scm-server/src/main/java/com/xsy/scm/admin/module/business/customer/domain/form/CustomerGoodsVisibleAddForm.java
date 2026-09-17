package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客户商品可见性 添加表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerGoodsVisibleAddForm {

    @Schema(description = "客户ID")
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "可见性：1 显示，2 屏蔽")
    @NotNull(message = "可见性不能为空")
    private Integer visibleType;
}
