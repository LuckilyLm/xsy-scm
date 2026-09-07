package com.xianshuyuan.scm.purchase.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PurchaseReceiptCreateRequest(
        @NotNull Long purchaseOrderId,
        @Size(max=500) String remark
) {}
