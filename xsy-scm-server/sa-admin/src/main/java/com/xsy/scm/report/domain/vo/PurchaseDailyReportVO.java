package com.xsy.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;
import net.lab1024.sa.base.common.domain.PageResult;

@Data
public class PurchaseDailyReportVO {
    private LocalDate reportDate;
    /** 空值表示未生成或当前用户无数据范围，不能解释为采购量为零。 */
    private OffsetDateTime generatedAt;
    private PageResult<ProductRow> products;

    @Data
    public static class ProductRow {
        private Long skuId;
        private String spuCode;
        private String productName;
        private String skuCode;
        private String skuName;
        private String purchaseUnit;
        private Long orderCount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal plannedQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal orderAmount;
    }
}
