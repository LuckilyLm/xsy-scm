package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量删除采购单。
 */
@Data
public class PurchaseOrderBatchDeleteForm {
    @Valid
    @NotEmpty(message = "采购单列表不能为空")
    @Size(max = 100, message = "采购单列表不能超过100项")
    private List<
            PurchaseOrderVersionForm> orders;
}
