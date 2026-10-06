package com.xsy.scm.purchase.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

/**
 * 采购单。
 *
 * <p>
 * <b>没有 {@code confirmedAt}</b>：{@code purchase_order} 表没有 {@code confirmed_at} 列，收货完成时间见各收货单的
 * {@code confirmed_at}。{@code submittedAt} / {@code cancelledAt} / {@code shortClosedAt} 与状态时间戳 CHECK 一一对应。
 *
 * <p>
 * {@code receivedProgress} 是<b>汇总进度</b>（派生量，不落库），用于列表展示。
 *
 * <p>
 * {@code items} / {@code allocations} / {@code logs} <b>仅 detail 返回</b>：{@code allocations} 是跨行扁平化的分配列表
 * （前端「一行多需求」编辑器用），与 {@code items[].allocations} 是同一批数据的不同切面。
 */
@Data
public class PurchaseOrderVO {
    private Long id;
    private String orderNo;
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private Long purchaserId;
    private String purchaserName;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private LocalDate plannedArrivalDate;
    private String status;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal totalAmount;
    /**
     * 派生：Σreceived / Σplanned（比例，scale 4，HALF_UP），用于列表进度展示。
     *
     * <p>
     * 无活动收货行时为 {@code null}，表示尚无进度值；超收时比例可大于 1。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receivedProgress;
    private String remark;
    private String cancelReason;
    private String shortCloseReason;
    private OffsetDateTime submittedAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime shortClosedAt;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<PurchaseOrderItemVO> items;
    private List<PurchaseOrderAllocationVO> allocations;
    private List<PurchaseOperationLogVO> logs;
}
