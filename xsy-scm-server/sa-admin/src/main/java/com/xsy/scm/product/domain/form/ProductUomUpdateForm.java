package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.product.constant.ScmUomCategoryEnum;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 计量单位编辑表单。编码与名称刻意不可改：商品与供应商关系表按单位名称字符串记账，改名会让既有数据指向一个字典里不存在的单位，只能停用旧单位再新建。
 */
@Data
public class ProductUomUpdateForm {
    @NotNull(message = "计量单位 ID不能为空")
    @Positive(message = "计量单位 ID必须大于0")
    private Long uomId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @NotBlank(message = "量纲分类不能为空")
    @ScmEnumValue(enumClass = ScmUomCategoryEnum.class, message = "计量单位类别无效")
    private String category;
    @NotNull(message = "小数精度不能为空")
    @Min(value = 0, message = "小数精度不能小于0")
    @Max(value = 6, message = "小数精度不能大于6")
    private Integer precisionScale;
    @NotNull(message = "计量单位状态不能为空")
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "计量单位状态无效")
    private String status;
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder;
}
