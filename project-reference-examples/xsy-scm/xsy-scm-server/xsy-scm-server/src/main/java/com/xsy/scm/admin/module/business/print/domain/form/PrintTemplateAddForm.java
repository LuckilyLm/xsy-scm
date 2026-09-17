package com.xsy.scm.admin.module.business.print.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 打印模板 添加表单
 *
 * @author xsy-scm
 */
@Data
public class PrintTemplateAddForm {

    @Schema(description = "模板编码")
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    @Schema(description = "模板名称")
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    @Schema(description = "业务类型：1 采购单，2 发货单，3 分拣小票，4 询价报价单")
    @NotNull(message = "业务类型不能为空")
    private Integer bizType;

    @Schema(description = "模板内容（HTML，使用 {{key}} 占位）")
    @NotBlank(message = "模板内容不能为空")
    private String content;

    @Schema(description = "纸张规格（A4 / 58mm / 80mm）")
    private String paperSize;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;
}
