package com.xianshuyuan.scm.purchase.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PurchaseReceiptCreateRequest(
        @NotNull Long purchaseOrderId,
        @Size(max = 500) String remark,
        com.xianshuyuan.scm.purchase.entity.PurchaseReceiptMode receiptMode
) {
    public PurchaseReceiptCreateRequest(Long purchaseOrderId, String remark) {
        this(purchaseOrderId, remark, null);
    }
}
