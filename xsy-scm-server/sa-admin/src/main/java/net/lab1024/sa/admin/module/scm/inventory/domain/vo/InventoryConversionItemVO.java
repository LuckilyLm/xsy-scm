package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

/**
 * 规格转换单明细行（独立 VO，供 mapper 与命令侧复用）。
 *
 * <p>与 {@link InventoryConversionVO.Item} 字段一致但**不复用同一个类**，
 * 理由与其它库存单据明细相同：头内嵌明细是「详情的组成部分」，
 * 独立投影是「一行的视图」，两者演进理由不同。
 *
 * <p>源与目标各带一套 SKU 编码 / 名称 / 商品名：转换的语义天然是「从哪到哪」，
 * 只显示一套会让用户必须点进详情才能确认方向。
 */
@Data
public class InventoryConversionItemVO {

    private Long id;

    private Long conversionId;

    private Long sourceSkuId;

    private String sourceSkuCode;

    private String sourceSkuName;

    private String sourceProductName;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal sourceQuantity;

    private String sourceUnit;

    private Long targetSkuId;

    private String targetSkuCode;

    private String targetSkuName;

    private String targetProductName;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal targetQuantity;

    private String targetUnit;

    private String remark;
}
