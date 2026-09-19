package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 出库单（头 + 明细）。
 *
 * <p>展示字段（仓库编码/名称、SKU 编码/名称、商品名）是**实时联表结果，不是快照** ——
 * 与库存余额同一取向：单据上真正需要冻结的是 {@code unitSnapshot}（在明细行上）。
 */
@Data
public class InventoryOutboundVO {

    private Long id;

    private String outboundNo;

    private Long warehouseId;

    private String warehouseCode;

    private String warehouseName;

    private String status;

    /** 状态中文描述（由服务层按枚举填充，便于列表直接展示）。 */
    private String statusDesc;

    private String remark;

    private OffsetDateTime confirmedAt;

    private String operator;

    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 明细；仅详情接口填充，列表接口为 null。 */
    private List<Item> items;

    /** 出库单明细行。 */
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

        /** 确认出库时写入的记账单位快照；草稿态为空。 */
        private String unitSnapshot;

        private String remark;
    }
}
