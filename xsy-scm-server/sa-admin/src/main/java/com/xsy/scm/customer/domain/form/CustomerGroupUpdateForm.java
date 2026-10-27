package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑客户分组；成员集合会整体替换为本表单的客户。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerGroupUpdateForm extends CustomerGroupAddForm {

    @NotNull(message = "分组 ID 不能为空")
    @Positive(message = "分组 ID 必须大于0")
    private Long groupId;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
