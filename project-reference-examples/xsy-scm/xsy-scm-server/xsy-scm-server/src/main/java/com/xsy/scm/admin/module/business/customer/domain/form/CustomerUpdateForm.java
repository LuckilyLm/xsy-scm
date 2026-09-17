package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客户 更新表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerUpdateForm extends CustomerAddForm {

    @Schema(description = "客户ID")
    @NotNull(message = "客户ID不能为空")
    private Long customerId;
}
