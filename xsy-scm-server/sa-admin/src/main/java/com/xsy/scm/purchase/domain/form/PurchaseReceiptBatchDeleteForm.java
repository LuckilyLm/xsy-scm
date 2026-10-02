package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量删除采购收货单。
 */
@Data
public class PurchaseReceiptBatchDeleteForm {
    @Valid
    @NotEmpty(message = "收货单列表不能为空")
    @Size(max = 100, message = "收货单列表不能超过100项")
    private List<PurchaseReceiptVersionForm> receipts;

    /**
     * `id + version` 双谓词。
     */
    @Data
    public static class PurchaseReceiptVersionForm {
        @NotNull(message = "收货单 ID 不能为空")
        private Long id;
        @NotNull(message = "版本号不能为空")
        @Min(value = 0, message = "版本号不能小于0")
        private Integer version;
    }
}
