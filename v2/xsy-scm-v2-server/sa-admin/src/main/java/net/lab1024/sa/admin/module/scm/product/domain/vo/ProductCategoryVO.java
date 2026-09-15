package net.lab1024.sa.admin.module.scm.product.domain.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
@Data
public class ProductCategoryVO {
private Long categoryId;
private Integer version;
private Long parentId;
private String categoryCode;
private String name;
private Integer level;
private Integer sortOrder;
private String status;
private String categoryPath;
}
