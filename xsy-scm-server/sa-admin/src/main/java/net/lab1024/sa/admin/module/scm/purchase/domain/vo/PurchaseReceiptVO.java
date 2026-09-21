package net.lab1024.sa.admin.module.scm.purchase.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 采购收货单（W5 Target Design §7.2；B1 扩展）。
 *
 * <p>2 状态 `DRAFT` / `CONFIRMED`；`receivedAt` 与 `confirmedAt` 同时写入，
 * `operator` 由 `ScmOperator.current()` 写入（修 A-D12 的 `SYSTEM` 硬编码）。
 * B1 增加入库方式 / 入库状态 / 入库时间 / 入库操作人（与 `status` 解耦）。
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
    private List<PurchaseReceiptItemVO> items;
}
