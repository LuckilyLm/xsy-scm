package com.xianshuyuan.scm.purchase.dto;

import com.xianshuyuan.scm.purchase.entity.ReceiptWeighingSource;
import jakarta.validation.constraints.NotNull;

public record PurchaseReceiptConfirmItemRequest(
        @NotNull Long receiptItemId,
        @NotNull Integer version,
        String receivedQuantity,
        String actualWeight,
        ReceiptWeighingSource weightSource,
        String correctionReason
) {}
