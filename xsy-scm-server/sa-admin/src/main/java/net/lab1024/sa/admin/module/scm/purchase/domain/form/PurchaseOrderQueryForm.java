package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import net.lab1024.sa.base.common.domain.PageParam;

/** 采购单列表查询条件（W5 Target Design §7.2）。 */
@Data @EqualsAndHashCode(callSuper=true)
public class PurchaseOrderQueryForm extends PageParam {
    @Size(max=64) private String orderNo;
    private Long supplierId;
    private Long purchaserId;
    private Long warehouseId;
    @Pattern(regexp="DRAFT|SUBMITTED|PARTIALLY_RECEIVED|RECEIVED|SHORT_CLOSED|CANCELLED") private String status;
    private OffsetDateTime createdFrom;
    private OffsetDateTime createdTo;
}
