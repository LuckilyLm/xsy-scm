package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 盘点单明细行（独立 VO，供 mapper 与命令侧复用）。
 *
 * <p>与 {@link InventoryStocktakeVO.Item} 字段一致但**不复用同一个类**，
 * 理由与出库单明细相同：头内嵌明细是「详情的组成部分」，独立投影是「一行的视图」，
 * 两者演进理由不同。用同名字段保持前端一致即可。
 */
@Data
public class InventoryStocktakeItemVO {

    private Long id;

    private Long stocktakeId;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String productName;

    private Map<String, String> specValues;

    /**
     * 账面量快照（保存草稿那一刻）。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal bookQuantity;

    /**
     * 实盘量。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal actualQuantity;

    /**
     * 差异 = 实盘量 − 账面量（派生值，仅展示）。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal deltaQuantity;

    /**
     * 确认时写入的记账单位快照；草稿态为空。
     */
    private String unitSnapshot;

    private String remark;
}
