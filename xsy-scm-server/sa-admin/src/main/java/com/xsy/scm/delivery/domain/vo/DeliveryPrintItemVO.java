package com.xsy.scm.delivery.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

/** 配送打印明细只包含纸质单据需要的字段，不返回内部审计信息。数量和金额统一使用四位小数字符串。 */
@Data
public class DeliveryPrintItemVO {
    private Long id;
    private Long orderId;
    private String productNameSnapshot;
    private String specNameSnapshot;
    private String saleUnitSnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal actualQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedLineAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal settlementLineAmount;
}
