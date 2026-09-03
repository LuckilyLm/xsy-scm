package com.xianshuyuan.scm.order.dto;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.util.List;
public record OrderReturnCreateRequest(@NotNull Long orderId,@NotBlank @Size(max=500) String reason,@NotEmpty List<@Valid Item> items){public record Item(@NotNull Long orderItemId,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=4) BigDecimal requestedQuantity){}}
