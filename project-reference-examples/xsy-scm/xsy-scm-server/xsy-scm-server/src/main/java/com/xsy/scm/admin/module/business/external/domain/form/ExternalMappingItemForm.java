package com.xsy.scm.admin.module.business.external.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 外部平台映射 单条导入项
 *
 * @author xsy-scm
 */
@Data
public class ExternalMappingItemForm {

    @Schema(description = "系统内ID")
    @NotNull(message = "系统内ID不能为空")
    private Long localId;

    @Schema(description = "外部平台ID")
    @NotBlank(message = "外部平台ID不能为空")
    private String externalId;

    @Schema(description = "单位转换系数（未填默认 1）")
    private BigDecimal convertRatio;
}
