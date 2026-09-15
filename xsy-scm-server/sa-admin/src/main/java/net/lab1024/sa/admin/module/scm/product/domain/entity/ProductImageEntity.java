package net.lab1024.sa.admin.module.scm.product.domain.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.Map;
import net.lab1024.sa.admin.module.scm.common.json.JsonbStringMapTypeHandler;
@Data
@TableName(value="product_image", autoResultMap=true)
public class ProductImageEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @Version private Integer version = 0;
    @TableLogic(value="false", delval="true") private Boolean deleted = false;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long spuId;
    private String fileKey;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    @TableField("is_primary") private Boolean primaryFlag;
    private Integer sortOrder;
}
