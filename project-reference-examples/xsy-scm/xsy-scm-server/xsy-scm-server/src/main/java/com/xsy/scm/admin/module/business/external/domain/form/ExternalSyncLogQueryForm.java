package com.xsy.scm.admin.module.business.external.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 外部平台同步日志 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ExternalSyncLogQueryForm extends PageParam {

    @Schema(description = "平台类型")
    private Integer systemType;

    @Schema(description = "业务单据类型")
    private Integer bizType;

    @Schema(description = "业务单据ID")
    private Long bizId;

    @Schema(description = "同步方向：1 上报，2 拉取")
    private Integer syncType;

    @Schema(description = "同步状态：1 成功，2 失败，3 待同步")
    private Integer syncStatus;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
