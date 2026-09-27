package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;

/**
 * 把一条采购需求分配到某个采购单行。
 *
 * <p>`version` 是**采购需求的版本**（不是采购单行的版本）：分配会改动
 * `purchase_demand.allocated_quantity` 与 `status`，因此必须带需求版本做乐观锁。
 * 缺失 → `PURCHASE_DEMAND_VERSION_REQUIRED(40091)`；不等 → `PURCHASE_DEMAND_VERSION_CONFLICT(40972)`。
 */
@Data
public class PurchaseDemandAllocateForm {
    @NotNull(message = "采购需求不能为空")
    private Long demandId;
    @NotNull(message = "采购单明细不能为空")
    private Long purchaseOrderItemId;
    @NotBlank(message = "分配数量不能为空")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String quantity;
    @NotNull(message = "供应商不能为空")
    private Long supplierId;
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;
    @NotNull(message = "需求版本不能为空")
    @Min(value = 0, message = "需求版本不能小于0")
    private Integer version;
}
