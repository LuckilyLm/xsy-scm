package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

/** 删除采购单（仅 DRAFT，否则 40993）（W5 Target Design §4.2 T6）。 */
@Data
public class PurchaseOrderDeleteForm {
    @NotNull private Long id;
}
