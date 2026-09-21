package net.lab1024.sa.admin.module.scm.purchase.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 采购单行（W5 Target Design §7.2）。
 *
 * <p>`remainingQuantity` / `overReceiptQuantity` 是**派生量**（不落库），
 * 与 P24 恒等式同口径：`remaining = max(planned − received, 0)`、`over = max(received − planned, 0)`。
 *
 * <p>`allocations` 是该行挂的全部需求分配（**Q13：一行多需求**），每项一个 `demandId`。
 */
@Data
public class PurchaseOrderItemVO {
    private Long id;
    private Long skuId;
    private String spuCode;
    private String productName;
    private String skuCode;
    private String skuName;
    private java.util.Map<String, Object> specValues;
    private String purchaseUnit;
    private String productType;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal plannedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receivedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal remainingQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal overReceiptQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal purchasePrice;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal lineAmount;
    private Integer sortOrder;
    private Integer version;
    private List<PurchaseOrderAllocationVO> allocations;
}
