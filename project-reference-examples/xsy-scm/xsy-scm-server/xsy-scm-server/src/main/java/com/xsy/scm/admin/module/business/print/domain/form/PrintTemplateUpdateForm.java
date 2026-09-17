package com.xsy.scm.admin.module.business.print.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 打印模板 更新表单
 *
 * @author xsy-scm
 */
@Data
public class PrintTemplateUpdateForm extends PrintTemplateAddForm {

    @Schema(description = "主键ID")
    @NotNull(message = "主键ID不能为空")
    private Long templateId;
}
