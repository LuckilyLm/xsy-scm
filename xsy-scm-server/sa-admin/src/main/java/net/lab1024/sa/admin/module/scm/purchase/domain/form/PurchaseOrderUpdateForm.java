package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

/**
 * 编辑采购单（仅 DRAFT）（W5 Target Design §7.2 / §7.4）。
 *
 * <p>`items[].id` 为空 = 新增行；非空 = 保留行（必须带 `version`，否则 40088）。
 * 未出现在 `items` 中的既有活动行 = 删除行（其全部 allocation 一并软删，P2/P3）。
 */
@Data @EqualsAndHashCode(callSuper=true)
public class PurchaseOrderUpdateForm extends PurchaseOrderAddForm {
    @NotNull private Long id;
    @NotNull @Min(0) private Integer version;
}
