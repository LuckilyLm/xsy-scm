package net.lab1024.sa.admin.module.scm.product.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
@Data public class ProductSkuOptionVO {
    private Long skuId; private Long spuId; private Long categoryId;
    private String skuCode; private String productName; private String specName;
    private Map<String,String> specValues;
    private String saleUnit; private String productType;
    private String status; private String spuStatus; private String categoryStatus;
    @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class)
    private BigDecimal marketPrice;
}
