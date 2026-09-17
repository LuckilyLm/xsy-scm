package com.xsy.scm.admin.module.business.trace.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 溯源批次 更新表单
 *
 * @author xsy-scm
 */
@Data
public class TraceBatchUpdateForm extends TraceBatchAddForm {

    @Schema(description = "主键ID")
    @NotNull(message = "主键ID不能为空")
    private Long batchId;
}
