package com.xsy.scm.admin.module.business.trace.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 检测报告 添加表单
 *
 * @author xsy-scm
 */
@Data
public class TraceInspectAddForm {

    @Schema(description = "关联溯源批次ID（绑定生产批号模式）")
    private Long batchId;

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "报告名称")
    @NotBlank(message = "报告名称不能为空")
    private String reportName;

    @Schema(description = "匹配模式：1 绑定采购单，2 绑定生产批号")
    @NotNull(message = "匹配模式不能为空")
    private Integer matchMode;

    @Schema(description = "报告图片文件")
    private String reportFile;

    @Schema(description = "报告PDF文件")
    private String pdfFile;

    @Schema(description = "检测日期")
    private LocalDate inspectDate;

    @Schema(description = "检测机构")
    private String inspectOrg;
}
