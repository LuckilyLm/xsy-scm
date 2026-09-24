package net.lab1024.sa.admin.module.scm.sorting.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 分拣任务明细行：计划量是冻结快照，分拣量与结果要么都还没有、要么都在。
 */
@Data
public class SortingTaskItemVO {
    private Long id;
    private Long taskId;
    private Long salesOrderId;
    private Long salesOrderItemId;
    private String orderNoSnapshot;
    private Long customerId;
    private String customerNameSnapshot;
    private Long spuId;
    private Long skuId;
    private String spuCodeSnapshot;
    private String productNameSnapshot;
    private String skuCodeSnapshot;
    private String specNameSnapshot;
    private String saleUnitSnapshot;
    private String productTypeSnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal plannedQuantitySnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal sortedQuantity;
    private String result;
    private String reason;
    private String sortedBy;
    private OffsetDateTime sortedAt;
    private String occupationStatus;
    private Integer version;
}
