package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmStrictDecimalStringDeserializer;

/**
 * 把一条采购需求分配到某个采购单行（W5 Target Design §7.4）。
 *
 * <p>`version` 是**采购需求的版本**（不是采购单行的版本）：分配会改动
 * `purchase_demand.allocated_quantity` 与 `status`，因此必须带需求版本做乐观锁。
 * 缺失 → `PURCHASE_DEMAND_VERSION_REQUIRED(40091)`；不等 → `PURCHASE_DEMAND_VERSION_CONFLICT(40972)`。
 */
@Data
public class PurchaseDemandAllocateForm {
    @NotNull private Long demandId;
    @NotNull private Long purchaseOrderItemId;
    @NotBlank @JsonDeserialize(using=ScmStrictDecimalStringDeserializer.class) private String quantity;
    @NotNull private Long supplierId;
    @NotNull private Long warehouseId;
    @NotNull @Min(0) private Integer version;
}
