package com.xsy.scm.admin.module.business.supplier.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 供应商商品提报 审核表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierProductApplyAuditForm {

    @Schema(description = "提报单ID")
    @NotNull(message = "提报单ID不能为空")
    private Long applyId;

    @Schema(description = "审核结果：2 已通过，3 已驳回")
    @NotNull(message = "审核结果不能为空")
    private Integer auditStatus;

    @Schema(description = "驳回原因")
    private String rejectReason;
}
