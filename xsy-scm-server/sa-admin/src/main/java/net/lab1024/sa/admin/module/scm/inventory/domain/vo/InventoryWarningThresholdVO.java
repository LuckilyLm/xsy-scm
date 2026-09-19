package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 预警阈值配置（一行 = 一个 (仓库, SKU)）。
 *
 * <p>展示字段（仓库编码/名称、SKU 编码/名称、商品名）是**实时联表结果，不是快照** ——
 * 与其它库存列表同一取向：配置表上没有需要冻结的事实。
 */
@Data
public class InventoryWarningThresholdVO {

    private Long id;

    private Long warehouseId;

    private String warehouseCode;

    private String warehouseName;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String productName;

    private Map<String, String> specValues;

    /** 预警下限；{@code null} = 不设下限。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal warnMin;

    /** 预警上限；{@code null} = 不设上限。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal warnMax;

    private String remark;

    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
