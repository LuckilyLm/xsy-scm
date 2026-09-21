package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 批量删除采购收货单（W5 Target Design §7.2）。
 */
@Data
public class PurchaseReceiptBatchDeleteForm {
    @Valid
    @NotEmpty
    @Size(max = 100)
    private List<PurchaseReceiptVersionForm> receipts;

    /**
     * `id + version` 双谓词。
     */
    @Data
    public static class PurchaseReceiptVersionForm {
        @NotNull
        private Long id;
        @NotNull
        @Min(0)
        private Integer version;
    }
}
