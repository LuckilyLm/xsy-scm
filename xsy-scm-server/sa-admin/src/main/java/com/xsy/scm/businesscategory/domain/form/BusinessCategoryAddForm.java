package com.xsy.scm.businesscategory.domain.form;

import com.xsy.scm.businesscategory.constant.BusinessCategoryScopeEnum;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessCategoryAddForm {

    @NotBlank(message = "分类作用域不能为空")
    @ScmEnumValue(enumClass = BusinessCategoryScopeEnum.class, message = "分类作用域无效")
    private String scope;

    /** 编码只在同一作用域内唯一，由调用方维护。 */
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 64, message = "分类编码不能超过64个字符")
    private String categoryCode;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称不能超过64个字符")
    private String name;

    @Positive(message = "上级分类 ID 必须大于0")
    private Long parentId;

    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder = 0;

    @NotNull(message = "分类状态不能为空")
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "分类状态无效")
    private String status;
}
