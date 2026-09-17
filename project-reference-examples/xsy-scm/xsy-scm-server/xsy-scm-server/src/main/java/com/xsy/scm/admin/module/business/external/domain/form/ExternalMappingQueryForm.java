package com.xsy.scm.admin.module.business.external.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 外部平台映射 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ExternalMappingQueryForm extends PageParam {

    @Schema(description = "平台类型")
    private Integer systemType;

    @Schema(description = "映射对象：1 商品，2 客户，3 供应商")
    private Integer bizType;

    @Schema(description = "系统内ID")
    private Long localId;

    @Schema(description = "外部平台ID")
    private String externalId;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
