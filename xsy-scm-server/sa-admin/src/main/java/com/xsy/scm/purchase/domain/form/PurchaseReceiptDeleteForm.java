package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * 删除采购收货单（仅 DRAFT，否则 40994）。
 */
@Data
public class PurchaseReceiptDeleteForm {
    @NotNull(message = "收货单 ID 不能为空")
    private Long id;
}
