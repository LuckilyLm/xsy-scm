package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.product.constant.ScmUomCategoryEnum;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductUomAddForm {
    @NotBlank(message = "单位编码不能为空")
    @Size(max = 64, message = "单位编码不能超过64个字符")
    private String uomCode;
    @NotBlank(message = "计量单位名称不能为空")
    @Size(max = 32, message = "计量单位名称不能超过32个字符")
    private String name;
    @NotBlank(message = "量纲分类不能为空")
    @ScmEnumValue(enumClass = ScmUomCategoryEnum.class, message = "计量单位类别无效")
    private String category;
    @NotNull(message = "小数精度不能为空")
    @Min(value = 0, message = "小数精度不能小于0")
    @Max(value = 6, message = "小数精度不能大于6")
    private Integer precisionScale = 4;
    @NotNull(message = "计量单位状态不能为空")
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "计量单位状态无效")
    private String status;
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder = 0;
}
