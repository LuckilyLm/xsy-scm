package net.lab1024.sa.admin.module.scm.customer.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.time.OffsetDateTime;
@Data @TableName("customer_sku_visibility") public class CustomerSkuVisibilityEntity {
 @TableId(type=IdType.AUTO) private Long id;
 @Version private Integer version=0;
 @TableLogic(value="false",delval="true") private Boolean deleted=false;
 private Long customerId;private Long skuId;
 private OffsetDateTime createdAt;private OffsetDateTime updatedAt;private String createdBy;private String updatedBy;
}
