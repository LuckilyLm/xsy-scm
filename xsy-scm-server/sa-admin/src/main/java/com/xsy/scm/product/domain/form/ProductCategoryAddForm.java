package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class ProductCategoryAddForm {
    @Positive(message = "上级分类 ID必须大于0")
    private Long parentId;
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 64, message = "分类编码不能超过64个字符")
    private String categoryCode;
    @NotBlank(message = "商品分类名称不能为空")
    @Size(max = 100, message = "商品分类名称不能超过100个字符")
    private String name;
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder = 0;
    @NotNull(message = "商品分类状态不能为空")
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "商品分类状态无效")
    private String status;
}
