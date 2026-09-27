package com.xsy.scm.purchase.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 采购收货单。
 *
 * <p>
 * 状态为 `DRAFT` / `CONFIRMED`。确认时间与确认操作人单独记录；入库方式、入库状态与入库时间 独立于收货单的商业状态。
 */
@Data
public class PurchaseReceiptVO {
    private Long id;
    private String receiptNo;
    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private Long supplierId;
    private String supplierName;
    private Long warehouseId;
    private String warehouseName;
    private String status;
    private String receiptMode;
    private String putawayStatus;
    private OffsetDateTime putawayAt;
    private String putawayBy;
    private OffsetDateTime receivedAt;
    private OffsetDateTime confirmedAt;
    private String operator;
    private String remark;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<
            PurchaseReceiptItemVO> items;
}
