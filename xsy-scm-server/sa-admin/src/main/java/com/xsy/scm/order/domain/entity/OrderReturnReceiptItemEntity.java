package com.xsy.scm.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("xsy_order_return_receipt_item")
public class OrderReturnReceiptItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receiptId;
    private Long returnItemId;
    private Long sourceSalesOutMovementId;
    private Long sourceOutboundItemId;
    private String disposition;
    /** 接收数量按销售单位；库存记账量单独冻结，历史退货这两列为空。 */
    private BigDecimal quantity;
    private String unitSnapshot;
    private BigDecimal stockQuantity;
    private String stockUnitSnapshot;
    private BigDecimal unitCost;
    private Long bundleComponentId;
    private Boolean bundleReceiptCounted = true;
    private OffsetDateTime createdAt;
    private String createdBy;
}
