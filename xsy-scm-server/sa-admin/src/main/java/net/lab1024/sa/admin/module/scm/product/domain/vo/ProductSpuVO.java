package net.lab1024.sa.admin.module.scm.product.domain.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
@Data
public class ProductSpuVO {
private Long spuId;
private Integer version;
private String spuCode;
private String name;
private String alias;
private Long categoryId;
private String description;
private String status;
private String categoryName;
private String categoryPath;
private Integer skuCount;
private ProductSkuVO defaultSku;
@JsonSerialize(using=ScmFixedScale4Serializer.class, nullsUsing=ScmFixedScale4Serializer.class)
private BigDecimal minMarketPrice;
@JsonSerialize(using=ScmFixedScale4Serializer.class, nullsUsing=ScmFixedScale4Serializer.class)
private BigDecimal maxMarketPrice;
private String primaryImageUrl;
private List<ProductSkuVO> skuList;
private OffsetDateTime updatedAt;
}
