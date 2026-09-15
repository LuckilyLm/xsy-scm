package net.lab1024.sa.admin.module.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑客户。
 *
 * <p>字段与 {@link CustomerAddForm} 完全一致，仅追加主键与乐观锁版本号；
 * <b>不包含 {@code status}</b>，更新路径不允许改状态（C7）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerUpdateForm extends CustomerAddForm {

    @NotNull
    @Positive
    private Long customerId;

    @NotNull
    @Min(0)
    private Integer version;
}
