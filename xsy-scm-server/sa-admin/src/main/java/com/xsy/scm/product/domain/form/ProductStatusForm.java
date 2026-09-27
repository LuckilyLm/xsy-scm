package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
public class ProductStatusForm extends ProductDeleteForm {
    @NotNull(message = "商品销售状态不能为空")
    @ScmEnumValue(enumClass = ScmShelfStatusEnum.class, message = "商品销售状态无效")
    private String status;
}
