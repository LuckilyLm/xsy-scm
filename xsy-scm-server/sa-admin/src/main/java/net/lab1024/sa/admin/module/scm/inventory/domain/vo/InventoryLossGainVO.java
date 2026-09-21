package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报损报溢单（头 + 明细）。
 *
 * <p>展示字段（仓库编码/名称、SKU 编码/名称、商品名）是**实时联表结果，不是快照** ——
 * 与库存余额、出库单、盘点单同一取向：单据上真正需要冻结的是明细行的
 * {@code quantity} 与 {@code unitSnapshot}。
 *
 * <p>{@code adjustTypeDesc} / {@code statusDesc} 由服务层按枚举填充，
 * 前端不硬编码字典（与出库单 / 盘点单一致）。
 */
@Data
public class InventoryLossGainVO {

    private Long id;

    private String lossGainNo;

    private Long warehouseId;

    private String warehouseCode;

    private String warehouseName;

    /**
     * {@code LOSS} 报损 / {@code OVERFLOW} 报溢。
     */
    private String adjustType;

    /**
     * 调整类型中文描述。
     */
    private String adjustTypeDesc;

    private String status;

    /**
     * 状态中文描述。
     */
    private String statusDesc;

    /**
     * 报损报溢原因（必填）。
     */
    private String reason;

    private String remark;

    /**
     * 审核时刻；仅已审核（通过 / 驳回）非空。
     */
    private OffsetDateTime auditedAt;

    /**
     * 审核人；仅已审核非空。
     */
    private String auditor;

    /**
     * 审核意见（驳回理由）。
     */
    private String auditOpinion;

    /**
     * 乐观锁版本号 —— **前端审批时必须原样回传**（见 {@code InventoryLossGainAuditForm}），
     * 否则「审批人看到的内容」与「审批的内容」可能不一致。
     */
    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /**
     * 明细；仅详情接口填充，列表接口为 null。
     */
    private List<Item> items;

    /**
     * 报损报溢单明细行。
     */
    @Data
    public static class Item {

        private Long id;

        private Long skuId;

        private String skuCode;

        private String skuName;

        private String productName;

        private Map<String, String> specValues;

        /**
         * 申报数量，恒为正；方向看单据的 {@code adjustType}。
         */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;

        /**
         * 审批通过时写入的记账单位快照；待审核态为空。
         */
        private String unitSnapshot;

        private String remark;
    }
}
