package com.xsy.scm.admin.module.business.trace.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 溯源码 返回对象
 *
 * @author xsy-scm
 */
@Data
public class TraceCodeVO {

    @Schema(description = "主键ID")
    private Long codeId;

    @Schema(description = "溯源码")
    private String traceCode;

    @Schema(description = "类型：1 批次码")
    private Integer codeType;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "关联溯源批次ID")
    private Long batchId;

    @Schema(description = "二维码图片")
    private String qrcodeUrl;

    @Schema(description = "状态：1 未启用，2 已启用，3 已作废")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
