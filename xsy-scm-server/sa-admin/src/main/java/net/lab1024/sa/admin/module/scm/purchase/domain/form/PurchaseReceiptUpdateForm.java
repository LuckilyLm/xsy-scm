package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

/** 编辑采购收货单（仅 DRAFT）：W5 只允许改备注（W5 Target Design §7.2）。 */
@Data
public class PurchaseReceiptUpdateForm {
    @NotNull private Long id;
    @NotNull @Min(0) private Integer version;
    @Size(max=500) private String remark;
}
