package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import net.lab1024.sa.base.common.domain.PageParam;

/** 采购收货单列表查询条件（W5 Target Design §7.2）。 */
@Data @EqualsAndHashCode(callSuper=true)
public class PurchaseReceiptQueryForm extends PageParam {
    @Size(max=64) private String receiptNo;
    private Long purchaseOrderId;
    private Long supplierId;
    private Long warehouseId;
    @Pattern(regexp="DRAFT|CONFIRMED") private String status;
    @Pattern(regexp="DIRECT|WAREHOUSE_CONFIRM") private String receiptMode;
    @Pattern(regexp="PENDING|COMPLETED") private String putawayStatus;
    private OffsetDateTime receivedFrom;
    private OffsetDateTime receivedTo;
}
