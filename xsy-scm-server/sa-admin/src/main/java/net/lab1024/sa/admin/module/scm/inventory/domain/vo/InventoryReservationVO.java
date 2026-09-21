package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 库存预留行。
 *
 * <p>展示字段（仓库 / SKU / 商品）实时联表，不是快照；{@code unitSnapshot} 才是预留时的快照。
 */
@Data
public class InventoryReservationVO {

    private Long id;

    private Long warehouseId;

    private String warehouseCode;

    private String warehouseName;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String productName;

    private String sourceDocumentType;

    private Long sourceDocumentId;

    private Long sourceDocumentItemId;

    /**
     * 来源单号（联销售订单取；取不到时为 null，不影响本行展示）。
     */
    private String sourceDocumentNo;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    private String unitSnapshot;

    private String status;

    /**
     * 状态中文描述（服务层按枚举填充）。
     */
    private String statusDesc;

    private OffsetDateTime occurredAt;

    private String operator;

    private OffsetDateTime createdAt;
}
