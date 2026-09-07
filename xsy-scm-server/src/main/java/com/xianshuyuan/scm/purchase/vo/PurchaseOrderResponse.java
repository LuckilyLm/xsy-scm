package com.xianshuyuan.scm.purchase.vo;

import com.xianshuyuan.scm.purchase.entity.PurchaseOrderStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PurchaseOrderResponse(Long id, Integer version, String orderNo, Long supplierId,
                                    String supplierCode, String supplierName, Long warehouseId,
                                    String warehouseCode, String warehouseName, Long purchaserId,
                                    LocalDate plannedArrivalDate, String remark, PurchaseOrderStatus status,
                                    String totalAmount, OffsetDateTime submittedAt, OffsetDateTime cancelledAt,
                                    String cancelReason, List<PurchaseOrderItemResponse> items,
                                    List<PurchaseDemandAllocationResponse> allocations,
                                    List<PurchaseOperationLogResponse> operationLogs) {
}
