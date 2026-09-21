package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

/**
 * 删除采购收货单（仅 DRAFT，否则 40994）（W5 Target Design §7.2）。
 */
@Data
public class PurchaseReceiptDeleteForm {
    @NotNull
    private Long id;
}
