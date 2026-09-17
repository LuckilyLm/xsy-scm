package com.xsy.scm.admin.module.business.finance.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 外部系统配置 添加表单
 *
 * @author xsy-scm
 */
@Data
public class ExternalConfigAddForm {

    @Schema(description = "系统类型：1 金蝶云星空，2 金蝶云星瀚，3 用友T+，4 用友U8，5 溯源平台，6 团餐平台")
    @NotNull(message = "系统类型不能为空")
    private Integer systemType;

    @Schema(description = "接口地址")
    private String apiUrl;

    @Schema(description = "应用 Key")
    private String appKey;

    @Schema(description = "应用密钥（服务端加密存储）")
    private String appSecret;

    @Schema(description = "字段映射配置（JSON）")
    private String fieldMappingJson;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;
}
