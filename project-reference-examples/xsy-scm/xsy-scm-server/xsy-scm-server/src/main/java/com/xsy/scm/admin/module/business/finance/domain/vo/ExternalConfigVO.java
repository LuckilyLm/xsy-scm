package com.xsy.scm.admin.module.business.finance.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部系统配置 返回对象
 *
 * <p>出于安全，不返回 appSecret；仅返回是否已配置密钥。</p>
 *
 * @author xsy-scm
 */
@Data
public class ExternalConfigVO {

    @Schema(description = "主键ID")
    private Long configId;

    @Schema(description = "系统类型：1 金蝶云星空，2 金蝶云星瀚，3 用友T+，4 用友U8，5 溯源平台，6 团餐平台")
    private Integer systemType;

    @Schema(description = "接口地址")
    private String apiUrl;

    @Schema(description = "应用 Key")
    private String appKey;

    @Schema(description = "是否已配置密钥")
    private Boolean secretConfigured;

    @Schema(description = "字段映射配置（JSON）")
    private String fieldMappingJson;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
