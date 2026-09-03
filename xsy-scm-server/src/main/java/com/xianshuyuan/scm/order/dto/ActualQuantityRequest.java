package com.xianshuyuan.scm.order.dto;
import jakarta.validation.constraints.*;
public record ActualQuantityRequest(@NotNull(message="版本号不能为空") @Min(value=0,message="版本号不正确") Integer version,@NotBlank(message="实数量不能为空") @Pattern(regexp="^\\d{1,14}(\\.\\d{1,4})?$",message="实数量格式不正确") String actualQuantity,@NotBlank(message="实重修改原因不能为空") String reason) {}
