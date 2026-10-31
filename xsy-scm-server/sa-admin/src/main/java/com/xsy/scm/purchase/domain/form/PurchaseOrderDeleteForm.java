package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * 删除采购单（仅尚未采购确认且没有收货记录的已提交单，否则 40993）。
 */
@Data
public class PurchaseOrderDeleteForm {
    @NotNull(message = "采购单 ID 不能为空")
    private Long id;
}
