package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 询价单 更新表单
 *
 * @author xsy-scm
 */
@Data
public class InquiryUpdateForm extends InquiryAddForm {

    @Schema(description = "主键ID")
    @NotNull(message = "主键ID不能为空")
    private Long inquiryId;
}
