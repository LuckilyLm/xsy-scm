package com.xsy.scm.admin.module.business.supplier.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 供应商对账单 确认 / 驳回表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierStatementConfirmForm {

    @Schema(description = "对账单ID")
    @NotNull(message = "对账单ID不能为空")
    private Long statementId;

    @Schema(description = "确认结果：2 供应商已确认，4 已驳回")
    @NotNull(message = "确认结果不能为空")
    private Integer status;
}
