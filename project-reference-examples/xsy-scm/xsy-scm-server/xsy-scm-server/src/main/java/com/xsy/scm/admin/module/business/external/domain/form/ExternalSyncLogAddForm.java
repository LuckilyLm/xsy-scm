package com.xsy.scm.admin.module.business.external.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部平台同步日志 登记表单
 *
 * <p>由对接执行器在同步完成后写入。</p>
 *
 * @author xsy-scm
 */
@Data
public class ExternalSyncLogAddForm {

    @Schema(description = "平台类型")
    @NotNull(message = "平台类型不能为空")
    private Integer systemType;

    @Schema(description = "业务单据类型")
    @NotNull(message = "业务单据类型不能为空")
    private Integer bizType;

    @Schema(description = "业务单据ID")
    @NotNull(message = "业务单据ID不能为空")
    private Long bizId;

    @Schema(description = "同步方向：1 上报，2 拉取")
    @NotNull(message = "同步方向不能为空")
    private Integer syncType;

    @Schema(description = "同步状态：1 成功，2 失败，3 待同步")
    @NotNull(message = "同步状态不能为空")
    private Integer syncStatus;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "同步时间")
    private LocalDateTime syncTime;
}
