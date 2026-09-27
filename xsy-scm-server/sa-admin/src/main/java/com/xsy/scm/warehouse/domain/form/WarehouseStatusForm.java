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
    @NotNull(message = "仓库 ID 不能为空")
    @Positive(message = "仓库 ID 必须大于0")
    private Long id;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
