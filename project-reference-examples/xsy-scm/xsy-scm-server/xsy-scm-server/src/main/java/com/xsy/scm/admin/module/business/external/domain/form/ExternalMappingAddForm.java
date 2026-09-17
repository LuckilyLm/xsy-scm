package com.xsy.scm.admin.module.business.external.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 外部平台映射 添加表单
 *
 * @author xsy-scm
 */
@Data
public class ExternalMappingAddForm {

    @Schema(description = "平台类型：1 金蝶云星空，2 金蝶云星瀚，3 用友T+，4 用友U8，5 溯源平台，6 团餐平台")
    @NotNull(message = "平台类型不能为空")
    private Integer systemType;

    @Schema(description = "映射对象：1 商品，2 客户，3 供应商")
    @NotNull(message = "映射对象不能为空")
    private Integer bizType;

    @Schema(description = "系统内ID")
    @NotNull(message = "系统内ID不能为空")
    private Long localId;

    @Schema(description = "外部平台ID")
    @NotBlank(message = "外部平台ID不能为空")
    private String externalId;

    @Schema(description = "单位转换系数（未填默认 1）")
    private BigDecimal convertRatio;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;
}
