package com.xianshuyuan.scm.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PurchaseReceiptConfirmRequest(
        @NotNull Integer version,
        @NotEmpty List<@Valid PurchaseReceiptConfirmItemRequest> items
) {}
