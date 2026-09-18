package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

/**
 * 新建采购收货单（W5 Target Design §7.2；B1 扩展 HD-B1-02）。
 *
 * <p>收货单的行由服务端**按采购单的全部活动行**自动生成（DRAFT 状态**不产生任何副作用**），
 * 因此表单只需要采购单 id、入库方式与备注。
 *
 * <p>**不允许直接填状态**（A16）：状态由 `confirm` 命令驱动。
 *
 * <p>**入库方式必选、无默认值**（HD-B1-02）：不允许隐藏成隐式行为。
 */
@Data
public class PurchaseReceiptCreateForm {
    @NotNull private Long purchaseOrderId;
    @NotNull @Pattern(regexp = "DIRECT|WAREHOUSE_CONFIRM") private String receiptMode;
    @Size(max=500) private String remark;
}
