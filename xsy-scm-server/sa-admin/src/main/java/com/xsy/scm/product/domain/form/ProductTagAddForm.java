package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductTagAddForm {
    @NotBlank(message = "标签编码不能为空")
    @Size(max = 64, message = "标签编码不能超过64个字符")
    private String tagCode;
    @NotBlank(message = "商品标签名称不能为空")
    @Size(max = 64, message = "商品标签名称不能超过64个字符")
    private String name;
    @NotNull(message = "商品标签状态不能为空")
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "商品标签状态无效")
    private String status;
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder = 0;
}
