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
    /**
     * 主档生命周期：ENABLED 可用 / DISABLED 停止新引用 / ARCHIVED 已归档，与 status（是否在售）正交。
     */
    private String masterStatus;
    private String mnemonicCode;
    private String brandName;
    private String origin;
    private String storageMethod;
    private List<ProductSpuTagVO> tags = new ArrayList<>();
    private String categoryName;
    private String categoryPath;
    private Integer skuCount;
    private ProductSkuVO defaultSku;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal minMarketPrice;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal maxMarketPrice;
    private String primaryImageUrl;
    private List<ProductSkuVO> skuList;
    private OffsetDateTime updatedAt;
}
