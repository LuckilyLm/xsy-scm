package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

/** `id + version` 双谓词表单：提交 / 删除等只需要乐观锁的命令共用（W5 Target Design §7.2）。 */
@Data
public class PurchaseOrderVersionForm {
    @NotNull private Long id;
    @NotNull @Min(0) private Integer version;
}
