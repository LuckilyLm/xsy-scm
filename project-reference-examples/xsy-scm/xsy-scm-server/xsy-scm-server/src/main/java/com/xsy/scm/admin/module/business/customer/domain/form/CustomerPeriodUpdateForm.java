package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客户账期 更新表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerPeriodUpdateForm extends CustomerPeriodAddForm {

    @Schema(description = "账期ID")
    @NotNull(message = "账期ID不能为空")
    private Long periodId;
}
