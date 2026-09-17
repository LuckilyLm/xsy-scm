package com.xsy.scm.admin.module.business.print.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 打印预览 表单
 *
 * <p>由业务模块传入单据数据（params），服务端按 <code>{{key}}</code> 占位渲染，避免前端篡改金额。</p>
 *
 * @author xsy-scm
 */
@Data
public class PrintPreviewForm {

    @Schema(description = "模板编码")
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    @Schema(description = "单据数据（键值对，用于填充模板占位符）")
    private Map<String, Object> params;
}
