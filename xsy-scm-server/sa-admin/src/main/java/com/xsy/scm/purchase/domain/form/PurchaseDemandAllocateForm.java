package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;

/** 需求分配请求携带采购需求本身的版本。分配会改变需求已分配量，因此必须用需求版本防止并发覆盖。 */
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
