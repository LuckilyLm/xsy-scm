package com.xianshuyuan.scm.purchase.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record PurchaseOrderSaveRequest(@NotNull Long supplierId, @NotNull Long warehouseId, Long purchaserId,
                                       LocalDate plannedArrivalDate, String remark, Integer version,
                                       @NotEmpty List<PurchaseOrderItemRequest> items) {
}
