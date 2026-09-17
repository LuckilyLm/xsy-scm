package com.xsy.scm.admin.module.business.external.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 外部平台映射 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ExternalMappingVO {

    @Schema(description = "主键ID")
    private Long mappingId;

    @Schema(description = "平台类型")
    private Integer systemType;

    @Schema(description = "映射对象：1 商品，2 客户，3 供应商")
    private Integer bizType;

    @Schema(description = "系统内ID")
    private Long localId;

    @Schema(description = "外部平台ID")
    private String externalId;

    @Schema(description = "单位转换系数")
    private BigDecimal convertRatio;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
