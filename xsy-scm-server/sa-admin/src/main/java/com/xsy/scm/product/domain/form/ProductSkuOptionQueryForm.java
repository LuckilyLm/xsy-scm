package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class ProductSkuOptionQueryForm {
    @Size(max = 150, message = "查询关键词不能超过150个字符")
    private String keyword;
    @ScmEnumValue(enumClass = ScmShelfStatusEnum.class, message = "SKU 状态无效")
    private String status;
    private Long spuId;
    @NotNull(message = "返回上限不能为空")
    @Min(value = 1, message = "返回上限不能小于1")
    @Max(value = 200, message = "返回上限不能大于200")
    private Integer limit = 50;
}
