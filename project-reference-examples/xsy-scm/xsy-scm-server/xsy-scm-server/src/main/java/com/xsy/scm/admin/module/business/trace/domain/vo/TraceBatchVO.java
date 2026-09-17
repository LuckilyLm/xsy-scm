package com.xsy.scm.admin.module.business.trace.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 溯源批次 返回对象
 *
 * @author xsy-scm
 */
@Data
public class TraceBatchVO {

    @Schema(description = "主键ID")
    private Long batchId;

    @Schema(description = "批次号")
    private String batchNo;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "厂商ID")
    private Long manufacturerId;

    @Schema(description = "生产批号")
    private String produceBatchNo;

    @Schema(description = "产地")
    private String originPlace;

    @Schema(description = "生产/采收日期")
    private LocalDate produceDate;

    @Schema(description = "保质期单位：1 天，2 月")
    private Integer shelfLifeUnit;

    @Schema(description = "保质期数值")
    private Integer shelfLifeValue;

    @Schema(description = "到期日期")
    private LocalDate expireDate;

    @Schema(description = "状态：1 有效，2 已过期，3 已作废")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
