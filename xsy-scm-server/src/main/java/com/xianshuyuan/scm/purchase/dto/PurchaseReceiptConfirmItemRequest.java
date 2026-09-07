package com.xianshuyuan.scm.purchase.dto;

import com.xianshuyuan.scm.purchase.entity.ReceiptWeighingSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PurchaseReceiptConfirmItemRequest(
        @NotNull Long receiptItemId,
        @NotNull Integer version,
        @NotBlank @Size(max = 32) String receivedQuantity,
        @Size(max = 32) String actualWeight,
        ReceiptWeighingSource weightSource,
        String correctionReason
) {
}
