package net.lab1024.sa.admin.module.scm.pricing.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

@Data
public class PriceHistoryVO {
    private Long historyId;
    private String source;
    private Long priceId;
    private Long customerId;
    private Long customerTypeId;
    private Long skuId;
    private String customerName;
    private String customerTypeName;
    private String skuCode;
    private String productName;
    private String specName;
    private String operationType;
    private String operator;
    private OffsetDateTime operatedAt;
    private Map<String, Object> beforeData;
    private Map<String, Object> afterData;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal currentUnitPrice;
    private OffsetDateTime currentEffectiveFrom;
    private OffsetDateTime currentEffectiveTo;
    private Boolean currentDeleted;
}
