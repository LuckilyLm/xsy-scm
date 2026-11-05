package com.xsy.scm.admin.module.system.menu.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜单 添加表单
 *
 */
@Data
public class MenuAddForm extends MenuBaseForm {

    @Schema(hidden = true)
    private Long createUserId;
}
