package com.xianshuyuan.scm.order.dto;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.util.List;
public record OrderReturnApproveRequest(@NotNull @Min(0) Integer version,@NotEmpty List<@Valid Item> items){public record Item(@NotNull Long returnItemId,@NotNull @Min(0) Integer version,@NotNull @DecimalMin("0") @Digits(integer=14,fraction=4) BigDecimal approvedQuantity){}}
