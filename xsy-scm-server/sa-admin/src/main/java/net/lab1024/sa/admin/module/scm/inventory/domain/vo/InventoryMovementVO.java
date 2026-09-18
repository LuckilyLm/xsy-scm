package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 库存流水行（W6 Target Design §10.1）。
 *
 * <p><b>溯源字段</b>：
 * <ul>
 *   <li>{@code sourceDocumentType + sourceDocumentItemId} —— 防重锚点（唯一索引列）；</li>
 *   <li>{@code sourceDocumentId + receiptNo} —— 人类可读溯源（Q9：不设 {@code movement_no}，
 *       来源单号由 {@code purchase_receipt.receipt_no} 承担），前端据此跳收货单详情。</li>
 * </ul>
 *
 * <p><b>快照 vs 实时</b>：{@code unitSnapshot} / {@code unitCost} / {@code beforeQuantity} /
 * {@code afterQuantity} / {@code occurredAt} / {@code operator} 是**写入时冻结的事实**；
 * SKU / 仓库的编码与名称是**实时联表**（流水表刻意不存展示快照，溯源靠 id）。
 */
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

    /** 收货单 id（头级溯源）。 */
    private Long sourceDocumentId;

    /** 收货行 id（防重锚点）。 */
    private Long sourceDocumentItemId;

    /** 收货单号（联 {@code purchase_receipt}），前端跳详情用。 */
    private String receiptNo;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    private String unitSnapshot;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unitCost;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal beforeQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal afterQuantity;

    /** 业务发生时刻 = 物理入库时刻：DIRECT 取收货确认时刻，WAREHOUSE_CONFIRM 取仓库确认入库时刻。 */
    private OffsetDateTime occurredAt;

    private String operator;

    private OffsetDateTime createdAt;
}
