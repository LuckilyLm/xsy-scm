package com.xsy.scm.admin.module.business.external.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 外部平台映射 批量导入表单
 *
 * @author xsy-scm
 */
@Data
public class ExternalMappingImportForm {

    @Schema(description = "平台类型")
    @NotNull(message = "平台类型不能为空")
    private Integer systemType;

    @Schema(description = "映射对象：1 商品，2 客户，3 供应商")
    @NotNull(message = "映射对象不能为空")
    private Integer bizType;

    @Schema(description = "映射明细")
    @Valid
    @NotEmpty(message = "映射明细不能为空")
    private List<ExternalMappingItemForm> items;
}
