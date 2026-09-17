package com.xsy.scm.admin.module.business.finance.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 外部系统配置 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ExternalConfigQueryForm extends PageParam {

    @Schema(description = "系统类型")
    private Integer systemType;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
