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
public class OrderReturnItemVO {
    private Long returnItemId;
    private Long returnId;
    private Long orderItemId;
    @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal requestedQuantity;
    @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal approvedQuantity;
    @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal lockedUnitPrice;
    @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal approvedAmount;
    private Integer version;
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
