package com.xsy.scm.admin.module.business.external.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 外部平台映射 更新表单
 *
 * @author xsy-scm
 */
@Data
public class ExternalMappingUpdateForm extends ExternalMappingAddForm {

    @Schema(description = "主键ID")
    @NotNull(message = "主键ID不能为空")
    private Long mappingId;
}
