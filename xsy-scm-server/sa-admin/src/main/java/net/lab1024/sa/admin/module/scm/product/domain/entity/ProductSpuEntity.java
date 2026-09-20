package net.lab1024.sa.admin.module.scm.product.domain.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.Map;
import net.lab1024.sa.admin.module.scm.common.json.JsonbStringMapTypeHandler;
@Data
@TableName(value="product_spu", autoResultMap=true)
public class ProductSpuEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @Version private Integer version = 0;
    @TableLogic(value="false", delval="true") private Boolean deleted = false;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private String spuCode;
    private String name;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String alias;
    private Long categoryId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String description;
    private String status;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String mnemonicCode;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String brandName;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String origin;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String storageMethod;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Integer shelfLifeDays;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private BigDecimal lossRate;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Integer purchaseWarningDays;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String invoiceName;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String taxCategoryCode;
    private Boolean taxExempt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private BigDecimal taxRate;
    private String masterStatus;
}
