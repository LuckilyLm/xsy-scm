package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客户商品别名 添加表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerProductAliasAddForm {

    @Schema(description = "客户ID")
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "别名")
    @NotBlank(message = "别名不能为空")
    private String aliasName;

    @Schema(description = "别名描述（≤50 字）")
    private String aliasDesc;

    @Schema(description = "副别名")
    private String subAliasName;

    @Schema(description = "副别名描述（≤50 字）")
    private String subAliasDesc;
}
