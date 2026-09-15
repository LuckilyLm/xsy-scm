package net.lab1024.sa.admin.module.scm.supplier.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/** 商品-供应商关联行。 */
@Data
public class SupplierSkuVO {

    private Long id;

    private Integer version;

    private Long supplierId;

    private Long skuId;

    private String supplierCodeSnapshot;

    private String supplierNameSnapshot;

    private String skuCodeSnapshot;

    private String skuNameSnapshot;

    private Map<String, String> specValuesSnapshot;

    private String purchaseUnit;

    /** 参考价，4 位定点字符串；{@code null} 保持 {@code null}。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal referencePrice;

    private Long purchaserId;

    private String purchaserName;

    private Boolean defaultFlag;

    private String status;

    private OffsetDateTime updatedAt;
}
