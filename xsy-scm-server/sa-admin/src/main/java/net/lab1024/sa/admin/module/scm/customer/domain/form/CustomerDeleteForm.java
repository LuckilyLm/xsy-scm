package net.lab1024.sa.admin.module.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 删除客户。
 *
 * <p>删除用 POST + body 传 {@code {customerId, version}}，统一乐观锁入口
 * （修正 legacy 的「GET + 无 version」形态，K10）。
 */
@Data
public class CustomerDeleteForm {

    @NotNull
    @Positive
    private Long customerId;

    @NotNull
    @Min(0)
    private Integer version;
}
