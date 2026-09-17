package com.xsy.scm.admin.module.business.trace.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 溯源码生成 表单
 *
 * @author xsy-scm
 */
@Data
public class TraceCodeGenerateForm {

    @Schema(description = "关联溯源批次ID")
    @NotNull(message = "溯源批次ID不能为空")
    private Long batchId;
}
