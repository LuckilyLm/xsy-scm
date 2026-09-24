package net.lab1024.sa.admin.module.scm.sorting.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 候选订单行：可进入分拣的 CONFIRMED 明细，及其将被冻结为计划量的实发口径。
 */
@Data
public class SortingCandidateLineVO {
    private Long salesOrderItemId;
    private Long salesOrderId;
    private String orderNo;
    private Long customerId;
    private String customerName;
    private Long spuId;
    private Long skuId;
    private String spuCodeSnapshot;
    private String productNameSnapshot;
    private String skuCodeSnapshot;
    private String specNameSnapshot;
    private String saleUnitSnapshot;
    private String productTypeSnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal actualQuantity;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime expectDeliveryTime;
}
