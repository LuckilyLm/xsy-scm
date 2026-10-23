package com.xsy.scm.purchase.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 采购缺口计算批次保留当时输入、每个商品规格的计算依据和分配建议，只读展示，不按现有库存重新计算。
 */
@Data
public class PurchaseDemandCalculationBatchDetailVO {

    private Long batchId;

    private String status;

    private OffsetDateTime startAt;

    private OffsetDateTime endAt;

    private Long warehouseId;

    private String warehouseName;

    private Long supplierId;

    private String supplierName;

    /**
     * 批次归属采购员（保存的 ID），可见性按它判定。
     */
    private Long purchaserId;

    private String purchaserName;

    private Long categoryId;

    private String keyword;

    private int sourceLineCount;

    private int candidateLineCount;

    private int generatedCount;

    private int skippedCount;

    private OffsetDateTime createdAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;

    private OffsetDateTime generatedAt;

    /**
     * 冻结的 SKU 级解释行，结构同 {@code PurchaseDemandCalculationBatchVO#summary}。
     */
    private List<Map<String, Object>> summary = new ArrayList<>();

    /**
     * 冻结的逐行建议。
     */
    private List<PurchaseDemandCalculationBatchItemVO> items = new ArrayList<>();
}
