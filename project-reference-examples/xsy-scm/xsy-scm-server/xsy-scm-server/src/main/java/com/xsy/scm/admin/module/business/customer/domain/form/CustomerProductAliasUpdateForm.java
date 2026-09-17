package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客户商品别名 更新表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerProductAliasUpdateForm extends CustomerProductAliasAddForm {

    @Schema(description = "主键ID")
    @NotNull(message = "主键ID不能为空")
    private Long aliasId;
}
