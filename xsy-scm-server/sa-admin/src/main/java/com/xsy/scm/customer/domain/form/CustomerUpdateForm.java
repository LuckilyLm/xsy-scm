package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑客户。
 *
 * <p>
 * 字段与 {@link CustomerAddForm} 完全一致，仅追加主键与乐观锁版本号； <b>不包含 {@code status}</b>；状态只能通过专用状态变更端点修改。
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
