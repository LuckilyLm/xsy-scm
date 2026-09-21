package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 规格转换单（头 + 明细）。
 *
 * <p>展示字段（仓库编码/名称、SKU 编码/名称、商品名）是**实时联表结果，不是快照** ——
 * 与其它库存单据同一取向：单据上真正需要冻结的是明细行的
 * {@code sourceUnit} / {@code targetUnit}（它们是折算关系的一部分）。
 */
@Data
public class InventoryConversionVO {

    private Long id;

    private String conversionNo;

    private Long warehouseId;

    private String warehouseCode;

    private String warehouseName;

    /**
     * {@code SPLIT} / {@code COMBINE}。
     */
    private String convertType;

    /**
     * 类型中文描述（服务层按枚举填充）。
     */
    private String convertTypeDesc;

    /**
     * {@code PENDING} / {@code COMPLETED} / {@code REJECTED}。
     */
    private String status;

    /**
     * 状态中文描述（服务层按枚举填充）。
     */
    private String statusDesc;

    private String reason;

    private String remark;

    private OffsetDateTime auditedAt;

    private String auditor;

    private String auditOpinion;

    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /**
     * 明细；仅详情接口填充，列表接口为 null。
     */
    private List<Item> items;

    /**
     * 转换明细行。
     */
    @Data
    public static class Item {

        private Long id;

        private Long sourceSkuId;

        private String sourceSkuCode;

        private String sourceSkuName;

        private String sourceProductName;

        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal sourceQuantity;

        /**
         * 源单位（单据声明，折算关系的一部分）。
         */
        private String sourceUnit;

        private Long targetSkuId;

        private String targetSkuCode;

        private String targetSkuName;

        private String targetProductName;

        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal targetQuantity;

        /**
         * 目标单位（单据声明）。
         */
        private String targetUnit;

        private String remark;
    }
}
