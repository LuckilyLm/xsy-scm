package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 调拨单（头 + 明细）。
 *
 * <p><b>源仓与目标仓各带一套编码 / 名称</b>（而不是一个 {@code warehouseName}）：
 * 调拨的语义天然是「从哪到哪」，列表页只显示一个仓名会让用户必须点进详情才能确认方向。
 * 展示字段是**实时联表结果，不是快照** —— 与其它库存单据同一取向。
 */
@Data
public class InventoryTransferVO {

    private Long id;

    private String transferNo;

    private Long fromWarehouseId;

    private String fromWarehouseCode;

    private String fromWarehouseName;

    private Long toWarehouseId;

    private String toWarehouseCode;

    private String toWarehouseName;

    private String status;

    /**
     * 状态中文描述（由服务层按枚举填充，便于列表直接展示）。
     */
    private String statusDesc;

    private String remark;

    /**
     * 发出时刻；草稿与已取消为空。
     */
    private OffsetDateTime shippedAt;

    /**
     * 发出人；草稿与已取消为空。
     */
    private String shippedBy;

    /**
     * 收货时刻；仅已完成非空。
     */
    private OffsetDateTime receivedAt;

    /**
     * 收货人；仅已完成非空。
     */
    private String receivedBy;

    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /**
     * 明细；仅详情接口填充，列表接口为 null。
     */
    private List<Item> items;

    /**
     * 调拨单明细行。
     */
    @Data
    public static class Item {

        private Long id;

        private Long skuId;

        private String skuCode;

        private String skuName;

        private String productName;

        private Map<String, String> specValues;

        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;

        /**
         * 发出时写入的源仓记账单位快照；草稿态为空。
         */
        private String unitSnapshot;

        private String remark;
    }
}
