package com.xianshuyuan.scm.purchase.dto;
import jakarta.validation.constraints.*;
public record PurchaseDemandAllocationRequest(@NotNull Long id,@NotNull Long purchaseOrderItemId,@NotNull @Positive String quantity,@NotNull Long supplierId,@NotNull Long warehouseId,@NotNull Integer version) {}
