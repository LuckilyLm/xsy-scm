package com.xsy.scm.admin.module.business.purchase.domain.vo;

import com.xsy.scm.admin.module.business.purchase.constant.PurchaseStatusEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购单 返回对象
 *
 * @author xsy-scm
 */
@Data
public class PurchaseOrderVO {

    @Schema(description = "采购单ID")
    private Long purchaseId;

    @Schema(description = "采购单号")
    private String purchaseNo;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "采购员ID")
    private Long buyerId;

    @Schema(description = "品类ID")
    private Long categoryId;

    @Schema(description = "采购预估金额（不含税）")
    private BigDecimal totalAmount;

    @Schema(description = "实际采购金额（不含税，按实重收货后）")
    private BigDecimal actualAmount;

    @Schema(description = "期望到货时间")
    private LocalDateTime expectArriveTime;

    @Schema(description = "采购单二维码地址")
    private String qrcodeUrl;

    @SchemaEnum(PurchaseStatusEnum.class)
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
