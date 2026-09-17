package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购收货单 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ReceiveVO {

    @Schema(description = "收货单ID")
    private Long receiveId;

    @Schema(description = "收货单号")
    private String receiveNo;

    @Schema(description = "收货类型：1 采购收货，2 无单收货")
    private Integer receiveType;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "采购单ID")
    private Long purchaseId;

    @Schema(description = "采购明细ID")
    private Long itemId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "本次收货数量")
    private BigDecimal receiveQuantity;

    @Schema(description = "本次实收重量（kg）")
    private BigDecimal receiveWeight;

    @Schema(description = "本次单价（不含税）")
    private BigDecimal unitPrice;

    @Schema(description = "收货人")
    private Long receiveBy;

    @Schema(description = "收货时间")
    private LocalDateTime receiveTime;

    @Schema(description = "状态：1 已收，2 已入库，3 已作废")
    private Integer status;

    @Schema(description = "收货标记：1 正常，2 少收，3 超收")
    private Integer receiveFlag;

    @Schema(description = "商品备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
