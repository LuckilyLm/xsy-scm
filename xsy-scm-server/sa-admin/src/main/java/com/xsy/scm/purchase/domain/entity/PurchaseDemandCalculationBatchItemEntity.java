package com.xsy.scm.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.xsy.scm.purchase.support.PurchaseJsonbTypeHandler;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

/** One frozen allocation of a SKU-level net gap to a sales-order source line. */
@Data
@TableName(value = "purchase_demand_calculation_batch_item", autoResultMap = true)
public class PurchaseDemandCalculationBatchItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Integer lineNo;
    private Long salesOrderId;
    private Long salesOrderItemId;
    private String salesOrderNoSnapshot;
    private OffsetDateTime sourceConfirmedAt;
    private Long spuId;
    private Long skuId;
    private String spuCodeSnapshot;
    private String productNameSnapshot;
    private String skuCodeSnapshot;
    private String skuNameSnapshot;
    @TableField(typeHandler = PurchaseJsonbTypeHandler.class)
    private Map<String, Object> specValuesSnapshot;
    private String demandUnitSnapshot;
    private String productTypeSnapshot;
    private BigDecimal sourceQuantity;
    private BigDecimal requiredQuantity;
    private Long existingDemandId;
}
