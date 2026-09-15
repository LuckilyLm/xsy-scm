package net.lab1024.sa.admin.module.scm.product.domain.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
@Data
public class ProductSkuVO {
private Long skuId;
private Integer version;
private String skuCode;
private String barcode;
private String specName;
private Map<String, String> specValues;
private String saleUnit;
private String productType;
@JsonSerialize(using=ScmFixedScale4Serializer.class, nullsUsing=ScmFixedScale4Serializer.class)
private BigDecimal marketPrice;
private String status;
private Boolean defaultFlag;
private Integer sortOrder;
}
