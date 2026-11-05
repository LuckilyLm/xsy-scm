package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑客户。字段与 {@link CustomerAddForm} 一致，只能新增主键与乐观锁版本号；状态与业务员可与其它字段一起提交，但变更状态需客户状态权限，改派业务员需客户分配权限。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerUpdateForm extends CustomerAddForm {

    @NotNull(message = "客户 ID 不能为空")
    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
