package com.xsy.scm.admin.module.business.screen.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据大屏配置 更新表单
 *
 * @author xsy-scm
 */
@Data
public class ScreenConfigUpdateForm extends ScreenConfigAddForm {

    @Schema(description = "主键ID")
    @NotNull(message = "主键ID不能为空")
    private Long screenId;
}
