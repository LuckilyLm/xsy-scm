package com.xianshuyuan.scm.order.dto;import jakarta.validation.constraints.*;public record OrderRefundCompleteRequest(@NotNull @Min(0) Integer version,@Size(max=128) String externalReference){}
