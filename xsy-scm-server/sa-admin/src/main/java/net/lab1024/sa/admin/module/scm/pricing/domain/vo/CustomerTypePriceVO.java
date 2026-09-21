package net.lab1024.sa.admin.module.scm.pricing.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

@Data
public class CustomerTypePriceVO {
    private Long customerTypePriceId;
    private Integer version;
    private Long customerTypeId;
    private String customerTypeCode;
    private String customerTypeName;
    private Long skuId;
    private String skuCode;
    private String productName;
    private String specName;
    private Map<String, String> specValues;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
    private OffsetDateTime updatedAt;
}
