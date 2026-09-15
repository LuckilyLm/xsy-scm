package net.lab1024.sa.admin.module.scm.product.domain.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.Map;
import net.lab1024.sa.admin.module.scm.common.json.JsonbStringMapTypeHandler;
@Data
@TableName(value="product_sku", autoResultMap=true)
public class ProductSkuEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @Version private Integer version = 0;
    @TableLogic(value="false", delval="true") private Boolean deleted = false;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long spuId;
    private String skuCode;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String barcode;
    private String specName;
    @TableField(typeHandler=JsonbStringMapTypeHandler.class) private Map<String, String> specValues;
    private String saleUnit;
    private String productType;
    private BigDecimal marketPrice;
    private String status;
    @TableField("is_default") private Boolean defaultFlag;
    private Integer sortOrder;
}
