package com.xsy.scm.admin.module.business.trace.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 检测报告 返回对象
 *
 * @author xsy-scm
 */
@Data
public class TraceInspectVO {

    @Schema(description = "主键ID")
    private Long inspectId;

    @Schema(description = "关联溯源批次ID")
    private Long batchId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "报告名称")
    private String reportName;

    @Schema(description = "匹配模式：1 绑定采购单，2 绑定生产批号")
    private Integer matchMode;

    @Schema(description = "报告图片文件")
    private String reportFile;

    @Schema(description = "报告PDF文件")
    private String pdfFile;

    @Schema(description = "检测日期")
    private LocalDate inspectDate;

    @Schema(description = "检测机构")
    private String inspectOrg;

    @Schema(description = "状态：1 有效，2 已作废")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
