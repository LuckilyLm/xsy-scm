package net.lab1024.sa.admin.module.scm.pricing.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
@Data @TableName(value="customer_type_price",autoResultMap=true)
public class CustomerTypePriceEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @Version private Integer version=0;
    @TableLogic(value="false",delval="true") private Boolean deleted=false;
    private Long customerTypeId;
    private Long skuId;
    private BigDecimal unitPrice;
    private OffsetDateTime effectiveFrom;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime effectiveTo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
