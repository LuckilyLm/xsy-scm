package net.lab1024.sa.admin.module.scm.order.domain.vo;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
import net.lab1024.sa.admin.module.scm.order.support.OrderJsonbTypeHandler;

@Data
public class SalesOrderItemVO {
    private Long itemId;
    private Long orderId;
    private Long spuId;
    private Long skuId;
    private String spuCodeSnapshot;
    private String productNameSnapshot;
    private String skuCodeSnapshot;
    private String specNameSnapshot;
    private Map<String, Object> specValuesSnapshot;
    private String saleUnitSnapshot;
    private String productTypeSnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal actualQuantity;
    private String actualQuantitySource;
    private String actualQuantityReason;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal draftUnitPrice;
    private String draftPriceSource;
    private Long draftPriceSourceId;
    private Boolean manualPriceOverride;
    private String manualPriceReason;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal lockedUnitPrice;
    private String lockedPriceSource;
    private Long lockedPriceSourceId;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedLineAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal settlementLineAmount;
    private Integer sortOrder;
    private Integer version;
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
