package com.xsy.scm.purchase.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

/**
 * 采购单展示提交、取消、短关等业务时间；收货完成时间取各收货单。收货进度是查询时计算的值，采购明细和需求分配在详情中展示。
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
    private Long accountId;
    private PurchaseAccountVO account;
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
    /** 锁单开关：只阻止补价，不改变采购状态；收货与应付照常。 */
    private Boolean lockedFlag;
    private OffsetDateTime lockedAt;
    /** 锁单人写成“姓名(userType:userId)”，与其它操作人字段一致。 */
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String lockedBy;
    /** MANUAL / AUTO_7_DAYS。 */
    private String lockReason;
    /** 提交时刻 + 7 天；草稿单为空。 */
    private OffsetDateTime autoLockAt;
    /** 待定价行数：列表据此显示「待定价」，不需要再拉明细。 */
    private Integer pendingPriceCount;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<PurchaseOrderItemVO> items;
    private List<PurchaseOrderAllocationVO> allocations;
    private List<PurchaseOperationLogVO> logs;
}
