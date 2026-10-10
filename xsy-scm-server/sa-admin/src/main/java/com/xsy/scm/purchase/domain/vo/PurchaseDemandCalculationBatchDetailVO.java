package com.xsy.scm.purchase.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 冻结批次回看（只读）。
 *
 * <p>
 * 三块内容，缺一不可地支撑「建议量逐项解释」：
 * <ul>
 * <li>批次头：冻结时的输入条件与生命周期计数；</li>
 * <li>{@code summary}：冻结的解释行（按仓库 + SKU + 需求单位），四位定点字符串原样取自快照， 说明每个 SKU 的订单需求、可用库存、在途、已有采购覆盖与净缺口；</li>
 * <li>{@code items}：冻结的逐行建议，说明净缺口被摊到哪张订单行的哪个数量上。</li>
 * </ul>
 *
 * <p>
 * 所有数字都来自冻结快照，服务端<b>不回表重算</b>：回看与生成必须逐字一致，否则「为什么当时建议 3」会在库存变化后变成另一个答案。
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

    /** 批次归属采购员（已解析落库值）；可见性按它判定。 */
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

    /** 冻结的 SKU 级解释行，结构同 {@code PurchaseDemandCalculationBatchVO#summary}。 */
    private List<Map<String, Object>> summary = new ArrayList<>();

    /** 冻结的逐行建议。 */
    private List<PurchaseDemandCalculationBatchItemVO> items = new ArrayList<>();
}
