package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 询价单 状态变更表单
 *
 * @author xsy-scm
 */
@Data
public class InquiryStatusForm {

    @Schema(description = "询价单ID")
    @NotNull(message = "询价单ID不能为空")
    private Long inquiryId;

    @Schema(description = "目标状态：3 已完成，4 已取消")
    @NotNull(message = "目标状态不能为空")
    private Integer status;
}
