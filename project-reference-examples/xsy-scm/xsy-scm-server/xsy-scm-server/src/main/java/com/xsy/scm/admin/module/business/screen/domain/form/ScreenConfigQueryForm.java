package com.xsy.scm.admin.module.business.screen.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 数据大屏配置 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ScreenConfigQueryForm extends PageParam {

    @Schema(description = "大屏编码")
    private Integer screenCode;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
