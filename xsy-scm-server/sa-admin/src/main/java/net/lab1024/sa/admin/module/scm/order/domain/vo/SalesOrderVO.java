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
public class SalesOrderVO {
    private Long orderId;
    private String orderNo;
    private Long customerId;
    private String customerCodeSnapshot;
    private String customerNameSnapshot;
    private String orderSource;
    private Long originalOrderId;
    private String supplementReason;
    private String status;
    @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal orderedTotalAmount;
    @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal settlementTotalAmount;
    private String settleModeSnapshot;
    private OffsetDateTime expectDeliveryTime;
    private Long sellerId;
    private String remark;
    private String cancelReason;
    private OffsetDateTime submittedAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime cancelledAt;
    private Integer version;
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
