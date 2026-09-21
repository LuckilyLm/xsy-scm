package net.lab1024.sa.admin.module.scm.product.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.Map;

import net.lab1024.sa.admin.module.scm.common.json.JsonbStringMapTypeHandler;

@Data
@TableName(value = "product_category", autoResultMap = true)
public class ProductCategoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @Version
    private Integer version = 0;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;
    private String categoryCode;
    private String name;
    private Integer level;
    private Integer sortOrder;
    private String status;
}
