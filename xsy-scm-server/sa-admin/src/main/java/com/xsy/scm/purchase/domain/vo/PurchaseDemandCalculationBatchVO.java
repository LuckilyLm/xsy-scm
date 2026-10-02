package com.xsy.scm.purchase.domain.vo;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class PurchaseDemandCalculationBatchVO {
    private Long batchId;
    private String status;
    private int sourceLineCount;
    private int candidateLineCount;
    private int generatedCount;
    private int skippedCount;
    private List<Map<String, Object>> summary;
}
