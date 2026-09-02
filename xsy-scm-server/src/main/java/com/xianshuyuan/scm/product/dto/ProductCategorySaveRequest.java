package com.xianshuyuan.scm.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductCategorySaveRequest(
    Long parentId,
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 64, message = "分类编码不能超过64个字符")
    String categoryCode,
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 100, message = "分类名称不能超过100个字符")
    String name,
    @NotNull(message = "排序不能为空")
    Integer sortOrder,
    @NotBlank(message = "分类状态不能为空")
    @Pattern(regexp = "ENABLED|DISABLED", message = "分类状态不正确")
    String status
) {
}
