package net.lab1024.sa.admin.module.scm.purchase.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 采购需求（W5 Target Design §7.2）。
 *
 * <p>`demandUnit` 是**需求单位**（来自销售订单行的 `sale_unit_snapshot`，Q17）；
 * `unallocatedQuantity = requiredQuantity − allocatedQuantity` 是派生量，不落库。
 */
@Data
public class PurchaseDemandVO {
    private Long id;
    private Long salesOrderId;
    private String salesOrderNoSnapshot;
    private Long salesOrderItemId;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private String productName;
    private Map<String, Object> specValues;
    private String demandUnit;
    private String productType;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal requiredQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal allocatedQuantity;
    /**
     * 派生：requiredQuantity − allocatedQuantity。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unallocatedQuantity;
    private Long supplierId;
    private String supplierName;
    private Long warehouseId;
    private String warehouseName;
    private String status;
    private LocalDate demandDate;
    private OffsetDateTime sourceConfirmedAt;
    private Integer version;
    private OffsetDateTime createdAt;
}
