package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 编辑采购单（仅 DRAFT）。
 *
 * <p>
 * {@code items[].id} 为空 = 新增行；非空 = 保留行（必须带 {@code version}，否则 40088）。未出现在 {@code items} 中的既有活动行 = 删除行（其全部 allocation
 * 一并软删）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrderUpdateForm extends PurchaseOrderAddForm {
    @NotNull(message = "采购单 ID 不能为空")
    private Long id;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
