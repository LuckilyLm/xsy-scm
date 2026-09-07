package com.xianshuyuan.scm.purchase.dto; import jakarta.validation.constraints.*; public record PurchaseOrderCancelRequest(@NotNull Integer version,@NotBlank String reason) {}
