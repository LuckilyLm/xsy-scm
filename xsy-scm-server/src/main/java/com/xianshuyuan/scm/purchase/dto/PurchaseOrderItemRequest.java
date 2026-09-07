package com.xianshuyuan.scm.purchase.dto;
import jakarta.validation.constraints.*; import java.time.*; import java.util.*;
public record PurchaseOrderItemRequest(Long id,@NotNull Long skuId,@NotNull @Positive String quantity,@NotNull @DecimalMin("0") String price, @NotNull Long demandId,Integer version) {}
