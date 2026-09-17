package com.xsy.scm.admin.module.business.external.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部平台同步日志 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ExternalSyncLogVO {

    @Schema(description = "主键ID")
    private Long logId;

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

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "同步时间")
    private LocalDateTime syncTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
