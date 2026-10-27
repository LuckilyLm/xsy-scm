package com.xsy.scm.businesscategory.domain.form;

import com.xsy.scm.businesscategory.constant.BusinessCategoryScopeEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BusinessCategoryTreeQueryForm {

    @NotBlank(message = "分类作用域不能为空")
    @ScmEnumValue(enumClass = BusinessCategoryScopeEnum.class, message = "分类作用域无效")
    private String scope;
}
