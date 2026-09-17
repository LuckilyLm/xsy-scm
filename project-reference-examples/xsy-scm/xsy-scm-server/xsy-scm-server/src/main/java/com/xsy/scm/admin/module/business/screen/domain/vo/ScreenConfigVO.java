package com.xsy.scm.admin.module.business.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据大屏配置 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ScreenConfigVO {

    @Schema(description = "主键ID")
    private Long screenId;

    @Schema(description = "大屏编码")
    private Integer screenCode;

    @Schema(description = "大屏名称")
    private String screenName;

    @Schema(description = "布局与指标配置（JSON）")
    private String layoutJson;

    @Schema(description = "刷新间隔（秒）")
    private Integer refreshInterval;

    @Schema(description = "排序维度")
    private String sortField;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
