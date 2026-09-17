package com.xsy.scm.admin.module.business.print.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 打印预览 返回对象
 *
 * @author xsy-scm
 */
@Data
public class PrintPreviewVO {

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "纸张规格")
    private String paperSize;

    @Schema(description = "渲染后的内容（HTML）")
    private String content;
}
