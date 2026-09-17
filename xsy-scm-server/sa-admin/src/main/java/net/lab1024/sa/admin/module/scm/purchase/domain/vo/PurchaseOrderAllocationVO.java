package net.lab1024.sa.admin.module.scm.purchase.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 采购单行上的单条需求分配（W5 Target Design §7.2 / Q13）。
 *
 * <p>身份是 `(purchaseOrderItemId, demandId)`；`allocationId` 是 `purchase_demand_allocation.id`。
 * `demandVersion` 供前端编辑时回传做乐观锁；`demandStatus` 让前端知道该需求是否已被别的行分满。
 */
@Data
public class PurchaseOrderAllocationVO {
    private Long allocationId;
    private Long demandId;
    private Long salesOrderId;
    private String salesOrderNo;
    private Long salesOrderItemId;
    private Long skuId;
    @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal quantity;
    /** 需求单位（Q17）：与行上的 `purchaseUnit` 不一致时不允许自动分配。 */
    private String demandUnit;
    private Integer demandVersion;
    private String demandStatus;
}
