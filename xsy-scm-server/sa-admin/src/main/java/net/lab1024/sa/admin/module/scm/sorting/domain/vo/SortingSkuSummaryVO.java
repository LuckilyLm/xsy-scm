package net.lab1024.sa.admin.module.scm.sorting.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

/**
 * 按商品汇总的一行：分组键含销售单位，因此本行内的量天然同单位。
 */
@Data
public class SortingSkuSummaryVO {
    private Long skuId;
    private String spuCodeSnapshot;
    private String productNameSnapshot;
    private String skuCodeSnapshot;
    private String specNameSnapshot;
    private String saleUnitSnapshot;
    private Integer lineCount;
    private Integer orderCount;
    private Integer taskCount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal plannedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal sortedQuantity;
    private Integer unprocessedCount;
}
