package com.xsy.scm.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xsy.scm.purchase.support.PurchaseJsonbTypeHandler;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

/** Immutable demand-calculation inputs; only lifecycle/result columns change after generation. */
@Data
@TableName(value = "purchase_demand_calculation_batch", autoResultMap = true)
public class PurchaseDemandCalculationBatchEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private Long warehouseId;
    private Long supplierId;
    private Long purchaserId;
    private Long categoryId;
    private String keyword;
    private String status;
    private int sourceLineCount;
    private int candidateLineCount;
    private int generatedCount;
    private int skippedCount;
    @TableField(typeHandler = PurchaseJsonbTypeHandler.class)
    private Map<String, Object> summarySnapshot;
    @TableField(typeHandler = PurchaseJsonbTypeHandler.class)
    private Map<String, Object> resultSnapshot;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime generatedAt;
}
