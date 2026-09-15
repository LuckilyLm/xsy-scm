package net.lab1024.sa.admin.module.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 删除客户类型。
 *
 * <p>legacy 没有删除端点；W2 新增（Target Design Q3），删除前检查是否仍被活动客户引用。
 */
@Data
public class CustomerTypeDeleteForm {

    @NotNull
    @Positive
    private Long typeId;

    @NotNull
    @Min(0)
    private Integer version;
}
