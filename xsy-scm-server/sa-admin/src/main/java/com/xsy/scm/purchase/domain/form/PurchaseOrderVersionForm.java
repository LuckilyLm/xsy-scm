package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * {@code id + version} 双谓词表单：提交 / 删除等只需要乐观锁的命令共用。
 */
@Data
public class PurchaseOrderVersionForm {
    @NotNull(message = "采购单 ID 不能为空")
    private Long id;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
