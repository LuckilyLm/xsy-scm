package com.xsy.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/** 库存流水保留来源类型和来源明细 ID 作为防重依据，来源单号供页面查看原单据。数量、成本及发生时间保存当时的事实；商品与仓库名称可以按当前主档展示。 */
@Data
public class InventoryMovementVO {

    private Long id;

    private Long warehouseId;

    private String warehouseCode;

    private String warehouseName;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String productName;

    private Map<String, String> specValues;

    private String movementType;

    private String sourceDocumentType;

    /**
     * 收货单 id（头级溯源）。
     */
    private Long sourceDocumentId;

    /**
     * 收货行 id（防重锚点）。
     */
    private Long sourceDocumentItemId;

    /**
     * 收货单号（联 {@code xsy_purchase_receipt}），前端跳详情用。仅 {@code PURCHASE_IN} 有值。
     */
    private String receiptNo;

    /**
     * 出库单号，关联 {@code xsy_inventory_outbound}。
     *
     * <p>
     * 仅 {@code SALES_OUT} 有值。与 {@code receiptNo} 分开两个字段而不是合并成 「来源单号」一个：两者的跳转目标不同（收货单 vs 出库单），前端要按类型渲染不同链接。
     */
    private String sourceDocumentNo;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    private String unitSnapshot;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unitCost;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal beforeQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal afterQuantity;

    /**
     * 业务发生时刻 = 物理入库时刻：DIRECT 取收货确认时刻，WAREHOUSE_CONFIRM 取仓库确认入库时刻。
     */
    private OffsetDateTime occurredAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String operator;

    private OffsetDateTime createdAt;
}
