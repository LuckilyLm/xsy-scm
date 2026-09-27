package com.xsy.scm.warehouse.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 仓库启停命令。
 *
 * <p>{@code enable} / {@code disable} 共用：靠乐观锁 {@code version} 防重复状态覆盖。
 */
@Data
public class WarehouseStatusForm {
    @NotNull
    @Positive
    private Long id;
    @NotNull
    @Min(0)
    private Integer version;
}
