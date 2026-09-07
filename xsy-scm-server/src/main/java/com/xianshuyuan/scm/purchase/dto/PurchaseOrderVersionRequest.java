package com.xianshuyuan.scm.purchase.dto;

import jakarta.validation.constraints.*;

public record PurchaseOrderVersionRequest(@NotNull Integer version) {
}
